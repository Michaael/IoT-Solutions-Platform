export function sortByNewestFirst<T>(
  items: readonly T[],
  getTime: (item: T) => string | number | Date,
  getTieBreaker?: (item: T) => string,
): T[] {
  if (items.length <= 1) {
    return [...items];
  }
  return [...items].sort((left, right) => {
    const byTime = new Date(getTime(right)).getTime() - new Date(getTime(left)).getTime();
    if (byTime !== 0) {
      return byTime;
    }
    if (getTieBreaker) {
      return getTieBreaker(right).localeCompare(getTieBreaker(left));
    }
    return 0;
  });
}
