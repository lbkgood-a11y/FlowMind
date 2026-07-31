export function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function isValidPhone(value: string) {
  return /^1[3-9]\d{9}$/.test(value);
}

export function pageAfterDelete(current: number, rowCount: number) {
  return rowCount === 1 && current > 1 ? current - 1 : current;
}

export async function mapWithConcurrency<T, R>(
  source: T[],
  concurrency: number,
  mapper: (item: T) => Promise<R>,
) {
  const results: PromiseSettledResult<R>[] = new Array(source.length);
  let cursor = 0;
  const worker = async () => {
    while (cursor < source.length) {
      const index = cursor++;
      const item = source[index];
      if (item === undefined) continue;
      try {
        results[index] = { status: 'fulfilled', value: await mapper(item) };
      } catch (reason) {
        results[index] = { reason, status: 'rejected' };
      }
    }
  };
  await Promise.all(
    Array.from({ length: Math.min(Math.max(1, concurrency), source.length) }, worker),
  );
  return results;
}
