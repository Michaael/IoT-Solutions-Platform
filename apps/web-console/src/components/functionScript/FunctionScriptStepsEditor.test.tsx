import { cleanup, fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithInspector } from "../../test/renderWithInspector";
import FunctionScriptStepsEditor from "./FunctionScriptStepsEditor";

describe("FunctionScriptStepsEditor", () => {
  afterEach(cleanup);

  it("keeps invalid JSON visible instead of switching to the visual editor", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithInspector(
      <FunctionScriptStepsEditor
        value={'{"steps":[{"type":"return","fields":{"ok":true}}]}'}
        onChange={onChange}
      />,
    );

    const jsonToggle = screen.getByRole("checkbox", { name: "Show JSON" });
    await user.click(jsonToggle);
    const editor = screen.getByRole("textbox");
    fireEvent.change(editor, { target: { value: "{invalid" } });
    await user.click(jsonToggle);

    expect(jsonToggle).toBeChecked();
    expect(editor).toHaveValue("{invalid");
  });

  it("returns to the visual editor after valid JSON is applied", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const body = '{"steps":[{"type":"return","fields":{"ok":true}}]}';
    renderWithInspector(<FunctionScriptStepsEditor value={body} onChange={onChange} />);

    const jsonToggle = screen.getByRole("checkbox", { name: "Show JSON" });
    await user.click(jsonToggle);
    await user.click(jsonToggle);

    expect(jsonToggle).not.toBeChecked();
    expect(document.querySelector("textarea.json-editor")).not.toBeInTheDocument();
    expect(onChange).toHaveBeenLastCalledWith(body);
  });
});
