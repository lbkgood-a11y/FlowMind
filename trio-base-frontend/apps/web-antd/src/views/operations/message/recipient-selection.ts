/** 只接受当前授权查询返回的用户选项，防止通过篡改 Select 值发送给越权用户。 */
export function authorizedRecipientIds(
  selectedIds: string[],
  authorizedIds: Set<string>,
) {
  const result = [...new Set(selectedIds)].filter((id) =>
    authorizedIds.has(id),
  );
  if (result.length !== selectedIds.length)
    throw new Error('MESSAGE_RECIPIENT_NOT_AUTHORIZED');
  return result;
}
