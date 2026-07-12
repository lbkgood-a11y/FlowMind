package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.dto.SaveDictItemRequest;
import com.triobase.service.auth.dto.SaveDictTypeRequest;
import com.triobase.service.auth.entity.SysDictItem;
import com.triobase.service.auth.entity.SysDictType;
import com.triobase.service.auth.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @GetMapping("/types")
    @RequirePermission("/api/v1/dictionaries:GET")
    public R<List<SysDictType>> listTypes(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status) {
        return R.ok(dictionaryService.listTypes(keyword, status));
    }

    @PostMapping("/types")
    @RequirePermission("/api/v1/dictionaries:POST")
    public R<SysDictType> createType(@RequestBody SaveDictTypeRequest request) {
        return R.ok(dictionaryService.createType(request));
    }

    @PutMapping("/types/{id}")
    @RequirePermission("/api/v1/dictionaries/*:PUT")
    public R<SysDictType> updateType(@PathVariable String id,
                                     @RequestBody SaveDictTypeRequest request) {
        return R.ok(dictionaryService.updateType(id, request));
    }

    @DeleteMapping("/types/{id}")
    @RequirePermission("/api/v1/dictionaries/*:DELETE")
    public R<String> deleteType(@PathVariable String id) {
        dictionaryService.deleteType(id);
        return R.ok("字典类型已删除");
    }

    @GetMapping("/items")
    @RequirePermission("/api/v1/dictionaries:GET")
    public R<List<SysDictItem>> listItems(@RequestParam(required = false) String dictCode,
                                          @RequestParam(required = false) Integer status) {
        return R.ok(dictionaryService.listItems(dictCode, status));
    }

    @GetMapping("/items/enabled/{dictCode}")
    @RequirePermission("/api/v1/dictionaries:GET")
    public R<List<SysDictItem>> enabledItems(@PathVariable String dictCode) {
        return R.ok(dictionaryService.enabledItems(dictCode));
    }

    @PostMapping("/items")
    @RequirePermission("/api/v1/dictionaries:POST")
    public R<SysDictItem> createItem(@RequestBody SaveDictItemRequest request) {
        return R.ok(dictionaryService.createItem(request));
    }

    @PutMapping("/items/{id}")
    @RequirePermission("/api/v1/dictionaries/*:PUT")
    public R<SysDictItem> updateItem(@PathVariable String id,
                                     @RequestBody SaveDictItemRequest request) {
        return R.ok(dictionaryService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    @RequirePermission("/api/v1/dictionaries/*:DELETE")
    public R<String> deleteItem(@PathVariable String id) {
        dictionaryService.deleteItem(id);
        return R.ok("字典项已删除");
    }
}
