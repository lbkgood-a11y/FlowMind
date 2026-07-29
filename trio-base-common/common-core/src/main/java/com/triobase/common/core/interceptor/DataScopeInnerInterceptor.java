package com.triobase.common.core.interceptor;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.context.DataScopeContextHolder;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DataScopeInnerInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataScopeInnerInterceptor.class);

    private static final Set<String> SELF_COLUMN_CANDIDATES = Set.of(
            "created_by", "user_id", "submitted_by");
    private static final Set<String> ORG_COLUMN_CANDIDATES = Set.of(
            "org_unit_id", "org_id");

    private final Set<String> tablesWithSelfColumn = ConcurrentHashMap.newKeySet();
    private final Set<String> tablesWithoutSelfColumn = ConcurrentHashMap.newKeySet();
    private final Set<String> tablesWithOrgColumn = ConcurrentHashMap.newKeySet();
    private final Set<String> tablesWithoutOrgColumn = ConcurrentHashMap.newKeySet();

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        DataScope scope = DataScopeContextHolder.get();
        if (scope == null || scope.allowsAll()) {
            return;
        }

        String sql = boundSql.getSql();
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (!(stmt instanceof Select selectStmt)) {
                throw new IllegalStateException("Scoped query is not a SELECT statement");
            }
            PlainSelect plainSelect;
            if (selectStmt instanceof PlainSelect) {
                plainSelect = (PlainSelect) selectStmt;
            } else {
                throw new IllegalStateException("Unsupported scoped SELECT shape: "
                        + selectStmt.getClass().getSimpleName());
            }
            Table table = extractMainTable(plainSelect);
            if (table == null) {
                throw new IllegalStateException("Scoped query has no enforceable main table");
            }
            String tableName = cleanTableName(table.getName());

            Expression extraWhere = buildScopeCondition(tableName, scope);
            if (extraWhere == null) {
                throw new IllegalStateException("Scoped query has no enforceable row predicate for table " + tableName);
            }

            Expression originalWhere = plainSelect.getWhere();
            if (originalWhere != null) {
                plainSelect.setWhere(new AndExpression(originalWhere, extraWhere));
            } else {
                plainSelect.setWhere(extraWhere);
            }
            setBoundSql(boundSql, selectStmt.toString());
        } catch (IllegalStateException e) {
            log.error("Data-scope SQL rewrite failed for mappedStatement={} — denying query. Reason: {}",
                    ms.getId(), e.getMessage());
            throw new BizException(AuthErrorCode.PERMISSION_DENIED);
        } catch (Exception e) {
            log.error("Data-scope enforcement failed; denying query mappedStatement={}", ms.getId(), e);
            throw new BizException(AuthErrorCode.PERMISSION_DENIED);
        }
    }

    private Table extractMainTable(PlainSelect plainSelect) {
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table table) {
            return table;
        }
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getFromItem() instanceof Table table) {
                    return table;
                }
            }
        }
        return null;
    }

    private Expression buildScopeCondition(String tableName, DataScope scope) {
        List<Expression> conditions = new ArrayList<>();

        String selfColumn = resolveSelfColumn(tableName);
        if (selfColumn != null && scope.allowsSelf()) {
            String userId = SecurityContextHolder.getUserId();
            if (userId != null && !userId.isBlank()) {
                conditions.add(new EqualsTo(
                        new Column(tableName + "." + selfColumn),
                        new StringValue(userId)));
            }
        }

        List<String> orgUnitIds = collectOrgUnitIds(scope);
        if (!orgUnitIds.isEmpty()) {
            String orgColumn = resolveOrgColumn(tableName);
            if (orgColumn != null) {
                ExpressionList<StringValue> orgList = new ExpressionList<>(
                        orgUnitIds.stream().map(StringValue::new).toList());
                conditions.add(new InExpression(
                        new Column(tableName + "." + orgColumn), orgList));
            }
        }

        if (conditions.isEmpty()) {
            return null;
        }
        Expression result = conditions.get(0);
        for (int i = 1; i < conditions.size(); i++) {
            result = new AndExpression(result, conditions.get(i));
        }
        return result;
    }

    private String resolveSelfColumn(String tableName) {
        if (tablesWithoutSelfColumn.contains(tableName)) {
            return null;
        }
        String cached = tablesWithSelfColumn.stream()
                .filter(k -> k.startsWith(tableName + ":"))
                .findFirst().map(k -> k.substring(k.indexOf(':') + 1)).orElse(null);
        if (cached != null) {
            return cached;
        }
        Set<String> columns = resolveTableColumns(tableName);
        if (columns == null) {
            return null;
        }
        if (columns.isEmpty()) {
            tablesWithoutSelfColumn.add(tableName);
            return null;
        }
        for (String candidate : SELF_COLUMN_CANDIDATES) {
            if (columns.contains(candidate)) {
                tablesWithSelfColumn.add(tableName + ":" + candidate);
                return candidate;
            }
        }
        tablesWithoutSelfColumn.add(tableName);
        return null;
    }

    private String resolveOrgColumn(String tableName) {
        if (tablesWithoutOrgColumn.contains(tableName)) {
            return null;
        }
        String cached = tablesWithOrgColumn.stream()
                .filter(k -> k.startsWith(tableName + ":"))
                .findFirst().map(k -> k.substring(k.indexOf(':') + 1)).orElse(null);
        if (cached != null) {
            return cached;
        }
        Set<String> columns = resolveTableColumns(tableName);
        if (columns == null) {
            return null;
        }
        if (columns.isEmpty()) {
            tablesWithoutOrgColumn.add(tableName);
            return null;
        }
        for (String candidate : ORG_COLUMN_CANDIDATES) {
            if (columns.contains(candidate)) {
                tablesWithOrgColumn.add(tableName + ":" + candidate);
                return candidate;
            }
        }
        tablesWithoutOrgColumn.add(tableName);
        return null;
    }

    private Set<String> resolveTableColumns(String tableName) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
        if (tableInfo == null) {
            return null;
        }
        Set<String> columns = new HashSet<>();
        String keyColumn = tableInfo.getKeyColumn();
        if (keyColumn != null) {
            columns.add(keyColumn);
        }
        for (TableFieldInfo field : tableInfo.getFieldList()) {
            if (field.getColumn() != null) {
                columns.add(field.getColumn());
            }
        }
        return columns;
    }

    private List<String> collectOrgUnitIds(DataScope scope) {
        if (scope.deniesAll()) {
            return List.of();
        }
        List<String> allowed = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        for (DataScope.Policy policy : scope.policies()) {
            for (DataScope.Dimension dim : policy.dimensions()) {
                String scopeType = dim.scopeType();
                if ("ALL".equalsIgnoreCase(scopeType) || "SELF".equalsIgnoreCase(scopeType)) {
                    continue;
                }
                if (CollectionUtils.isNotEmpty(dim.orgUnitIds())) {
                    if ("DENY".equalsIgnoreCase(policy.effect())) {
                        denied.addAll(dim.orgUnitIds());
                    } else if ("ALLOW".equalsIgnoreCase(policy.effect())) {
                        allowed.addAll(dim.orgUnitIds());
                    }
                }
            }
        }
        if (!denied.isEmpty()) {
            Set<String> denySet = new HashSet<>(denied);
            allowed.removeIf(denySet::contains);
        }
        return allowed;
    }

    private String cleanTableName(String name) {
        if (name == null) {
            return "";
        }
        return name.replace("\"", "").replace("`", "").toLowerCase();
    }

    /**
     * Reflectively sets the SQL string on BoundSql.
     * On Java 17+ with strict module enforcement, the JVM may require:
     * --add-opens java.base/java.lang=ALL-UNNAMED
     * --add-opens java.base/java.lang.reflect=ALL-UNNAMED
     */
    private void setBoundSql(BoundSql boundSql, String sql) {
        try {
            Field field = BoundSql.class.getDeclaredField("sql");
            if (!field.trySetAccessible()) {
                throw new IllegalStateException("BoundSql.sql is not accessible");
            }
            field.set(boundSql, sql);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to apply data-scope predicate", e);
        }
    }
}
