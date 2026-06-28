import { Fragment, useContext, useRef, type CSSProperties, type ReactNode } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { JournalScrollContext } from "./JournalViewShell";

interface JournalVirtualListProps<T> {
  items: T[];
  estimateSizePx?: number;
  className?: string;
  getKey: (item: T, index: number) => string;
  renderItem: (item: T, style?: CSSProperties) => ReactNode;
}

export default function JournalVirtualList<T>({
  items,
  estimateSizePx = 100,
  className = "event-journal-list dash-virtual-list",
  getKey,
  renderItem,
}: JournalVirtualListProps<T>) {
  const scrollContext = useContext(JournalScrollContext);
  const listRef = useRef<HTMLUListElement>(null);

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => scrollContext?.current ?? listRef.current,
    estimateSize: () => estimateSizePx,
    overscan: 6,
    enabled: items.length > 0,
  });

  const virtualItems = virtualizer.getVirtualItems();

  return (
    <ul ref={listRef} className={className}>
      {items.length > 0 && (
        <li
          aria-hidden="true"
          className="dash-virtual-list-spacer"
          style={{ height: virtualizer.getTotalSize() }}
        />
      )}
      {virtualItems.map((virtualItem) => {
        const item = items[virtualItem.index];
        return (
          <Fragment key={getKey(item, virtualItem.index)}>
            {renderItem(item, { transform: `translateY(${virtualItem.start}px)` })}
          </Fragment>
        );
      })}
    </ul>
  );
}
