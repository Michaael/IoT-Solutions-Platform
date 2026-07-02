import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { FunctionDescriptor } from "../types";
import { renderWithInspector } from "../test/renderWithInspector";
import EditDescriptorDialog from "./EditDescriptorDialog";
import * as api from "../api";

vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return { ...actual, upsertFunction: vi.fn(), upsertEvent: vi.fn() };
});

vi.mock("./schema/DataSchemaEditor", () => ({
  default: () => <div data-testid="schema-editor" />,
}));

vi.mock("./functionScript/FunctionScriptStepsEditor", () => ({
  default: ({ value, onChange }: { value: string; onChange: (next: string) => void }) => (
    <textarea aria-label="Steps source" value={value} onChange={(event) => onChange(event.target.value)} />
  ),
}));

vi.mock("./functionScript/JavaFunctionEditor", () => ({
  default: ({ value, onChange }: { value: string; onChange: (next: string) => void }) => (
    <textarea aria-label="Java source" value={value} onChange={(event) => onChange(event.target.value)} />
  ),
}));

const javaFunction: FunctionDescriptor = {
  name: "agentFunction",
  description: "Created by agent",
  inputSchema: {
    name: "agentFunctionInput",
    fields: [{ name: "value", type: "STRING", description: "Value", nullable: false }],
  },
  outputSchema: {
    name: "agentFunctionOutput",
    fields: [{ name: "ok", type: "BOOLEAN", description: "Success", nullable: false }],
  },
  sourceType: "java",
  sourceBody: "public class AgentFunction { /* original */ }",
  dataSourcePath: "root.platform.data-sources.main",
  version: "1.2.3",
};

describe("EditDescriptorDialog", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    vi.mocked(api.upsertFunction).mockResolvedValue(javaFunction);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  function renderDialog(initial: FunctionDescriptor = javaFunction) {
    return renderWithInspector(
      <QueryClientProvider client={queryClient}>
        <EditDescriptorDialog
          objectPath="root.platform.functions"
          kind="function"
          initial={initial}
          onClose={vi.fn()}
          onSaved={vi.fn()}
        />
      </QueryClientProvider>,
    );
  }

  it("preserves independent Java and steps drafts when switching source type", async () => {
    const user = userEvent.setup();
    renderDialog();

    const sourceType = screen.getByRole("combobox", { name: "Source type" });
    const javaEditor = await screen.findByRole("textbox", { name: "Java source" });
    fireEvent.change(javaEditor, { target: { value: "custom Java body" } });

    await user.selectOptions(sourceType, "script");
    const stepsEditor = screen.getByRole("textbox", { name: "Steps source" });
    fireEvent.change(stepsEditor, { target: { value: '{"steps":[{"type":"return"}]}' } });

    await user.selectOptions(sourceType, "java");
    expect(await screen.findByRole("textbox", { name: "Java source" })).toHaveValue("custom Java body");
    await user.selectOptions(sourceType, "script");
    expect(screen.getByRole("textbox", { name: "Steps source" })).toHaveValue(
      '{"steps":[{"type":"return"}]}',
    );
  });

  it("keeps an agent-created descriptor payload through advanced JSON round-trip", async () => {
    const user = userEvent.setup();
    const sourceBody = '{"steps":[{"type":"agent_extension","config":{"keep":true}}]}';
    const initial: FunctionDescriptor = { ...javaFunction, sourceType: "script", sourceBody };
    renderDialog(initial);

    const advanced = screen.getByRole("checkbox", { name: "Edit as JSON (advanced)" });
    await user.click(advanced);
    const advancedEditor = document.querySelector("textarea.json-editor") as HTMLTextAreaElement;
    expect(JSON.parse(advancedEditor.value).sourceBody).toBe(sourceBody);
    await user.click(advanced);
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.upsertFunction).toHaveBeenCalledTimes(1));
    expect(api.upsertFunction).toHaveBeenCalledWith("root.platform.functions", initial);
  });

  it("does not leave advanced mode while its JSON is invalid", async () => {
    const user = userEvent.setup();
    renderDialog();

    const advanced = screen.getByRole("checkbox", { name: "Edit as JSON (advanced)" });
    await user.click(advanced);
    const advancedEditor = document.querySelector("textarea.json-editor") as HTMLTextAreaElement;
    fireEvent.change(advancedEditor, { target: { value: "{invalid" } });
    await user.click(advanced);

    expect(advanced).toBeChecked();
    expect(advancedEditor).toHaveValue("{invalid");
    expect(screen.getByText("Invalid schema JSON")).toBeInTheDocument();
  });
});
