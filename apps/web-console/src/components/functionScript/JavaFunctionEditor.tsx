import { useMemo } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { java } from "@codemirror/lang-java";
import { vscodeDark } from "@uiw/codemirror-theme-vscode";
import { EditorView } from "@codemirror/view";

interface JavaFunctionEditorProps {
  value: string;
  onChange: (next: string) => void;
  readOnly?: boolean;
}

export default function JavaFunctionEditor({
  value,
  onChange,
  readOnly = false,
}: JavaFunctionEditorProps) {
  const extensions = useMemo(
    () => [java(), EditorView.lineWrapping],
    []
  );

  return (
    <div className="java-function-editor">
      <CodeMirror
        value={value}
        height="420px"
        theme={vscodeDark}
        extensions={extensions}
        onChange={onChange}
        readOnly={readOnly}
        basicSetup={{
          lineNumbers: true,
          highlightActiveLineGutter: true,
          highlightActiveLine: true,
          foldGutter: true,
          bracketMatching: true,
          indentOnInput: true,
          tabSize: 4,
        }}
      />
    </div>
  );
}
