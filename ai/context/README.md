# ContextPack (FW-41)

Versioned AI context for bundle generation. Built from docs and `examples/*/bundle.json`.

```bash
python tools/ai-pack/build.py
```

Output:

- `ai/context/generated/ispf-context-pack.json`
- `packages/ispf-server/src/main/resources/ai/context-pack.json` (classpath for server)
