import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const localesRoot = path.resolve(__dirname, "../src/locales");
const source = "en";
const targets = ["ru", "de", "zh"];

function flattenKeys(obj, prefix = "") {
  return Object.keys(obj).sort();
}

let failed = false;
const sourceDir = path.join(localesRoot, source);
for (const file of fs.readdirSync(sourceDir).filter((name) => name.endsWith(".json")).sort()) {
  const canonical = JSON.parse(fs.readFileSync(path.join(sourceDir, file), "utf8"));
  const canonicalKeys = flattenKeys(canonical);
  for (const target of targets) {
    const targetPath = path.join(localesRoot, target, file);
    if (!fs.existsSync(targetPath)) {
      console.error(`Missing ${path.relative(localesRoot, targetPath)}`);
      failed = true;
      continue;
    }
    const targetData = JSON.parse(fs.readFileSync(targetPath, "utf8"));
    const targetKeys = flattenKeys(targetData);
    const missing = canonicalKeys.filter((key) => !targetKeys.includes(key));
    const extra = targetKeys.filter((key) => !canonicalKeys.includes(key));
    if (missing.length || extra.length) {
      failed = true;
      console.error(`${target}/${file}: missing=${missing.length} extra=${extra.length}`);
      if (missing.length) {
        console.error("  missing:", missing.slice(0, 5).join(", "));
      }
      if (extra.length) {
        console.error("  extra:", extra.slice(0, 5).join(", "));
      }
    }
  }
}

if (failed) {
  process.exit(1);
}
console.log("i18n key check OK");
