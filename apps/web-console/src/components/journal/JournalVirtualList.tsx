import { Fragment, useMemo, type ReactNode } from "react";
import { sortByNewestFirst } from "../../utils/journalSort";

interface JournalVirtualListProps<T> {
  items: T[];
  /** @deprecated kept for call-site compatibility; journals use a plain list (≤200 rows). */
  estimateSizePx?: number;
  className?: string;
  getTime: (item: T) => string | number | Date;
  getKey: (item: T, index: number) => string;
  renderItem: (item: T) => ReactNode;
}

/** Renders journal rows newest-first in normal document flow. */
export default function JournalVirtualList<T>({
  items,
  className = "event-journal-list",
  getTime,
  getKey,
  renderItem,
}: JournalVirtualListProps<T>) {
  const sorted = useMemo(
    () => sortByNewestFirst(items, getTime, (item) => getKey(item, 0)),
    [getKey, getTime, items],
  );

  return (
    <ul className={className}>
      {sorted.map((item, index) => (
        <Fragment key={getKey(item, index)}>
          {renderItem(item)}
        </Fragment>
      ))}
    </ul>
  );
}
