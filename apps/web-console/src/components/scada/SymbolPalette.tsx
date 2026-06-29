import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { SYMBOL_CATEGORIES, listSymbolsByCategory } from "../../scada/symbols/registry";

interface SymbolPaletteProps {
  selectedSymbolId: string | null;
  onSelectSymbol: (symbolId: string) => void;
}

export default function SymbolPalette({ selectedSymbolId, onSelectSymbol }: SymbolPaletteProps) {
  const { t } = useTranslation("scada");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<string>("process");

  const symbols = useMemo(() => {
    const base = listSymbolsByCategory(category);
    if (!search.trim()) return base;
    const q = search.toLowerCase();
    return listSymbolsByCategory(category).filter(
      (s) => t(s.nameKey).toLowerCase().includes(q) || s.id.includes(q)
    );
  }, [category, search, t]);

  return (
    <div className="scada-palette">
      <input
        type="search"
        placeholder={t("palette.search")}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <div className="scada-palette-categories">
        {SYMBOL_CATEGORIES.map((cat) => (
          <button
            key={cat}
            type="button"
            className={category === cat ? "active" : ""}
            onClick={() => setCategory(cat)}
          >
            {t(`categories.${cat}`)}
          </button>
        ))}
      </div>
      <div className="scada-palette-list">
        {symbols.map((sym) => (
          <button
            key={sym.id}
            type="button"
            className={`scada-palette-item${selectedSymbolId === sym.id ? " active" : ""}`}
            onClick={() => onSelectSymbol(sym.id)}
            title={sym.id}
          >
            <span className="scada-palette-item-id">{t(sym.nameKey)}</span>
            <span className="scada-palette-item-meta">{sym.defaultWidth}×{sym.defaultHeight}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
