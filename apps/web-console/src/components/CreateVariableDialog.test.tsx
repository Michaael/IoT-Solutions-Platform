import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CreateVariableDialog from "./CreateVariableDialog";
import * as api from "../api";
import { renderWithInspector } from "../test/renderWithInspector";

vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return {
    ...actual,
    createVariable: vi.fn(),
  };
});

describe("CreateVariableDialog", () => {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { mutations: { retry: false } },
    });
    vi.mocked(api.createVariable).mockResolvedValue({
      name: "temperature",
      value: { schema: { name: "temperature", fields: [] }, rows: [] },
      readable: true,
      writable: false,
      updatedAt: "2026-06-30T00:00:00.000Z",
      historyEnabled: false,
      historyRetentionDays: null,
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  function renderDialog() {
    return renderWithInspector(
      <QueryClientProvider client={queryClient}>
        <CreateVariableDialog
          objectPath="root.platform.devices.lab"
          onClose={onClose}
          onSaved={onSaved}
        />
      </QueryClientProvider>,
    );
  }

  it("creates a variable with entered name", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByPlaceholderText("myVariable"), "temperature");
    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => expect(api.createVariable).toHaveBeenCalled());
    expect(api.createVariable).toHaveBeenCalledWith(
      "root.platform.devices.lab",
      expect.objectContaining({
        name: "temperature",
        readable: true,
        writable: false,
      }),
    );
    expect(onSaved).toHaveBeenCalled();
  });

  it("calls onClose from cancel button", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onClose).toHaveBeenCalled();
    expect(api.createVariable).not.toHaveBeenCalled();
  });

  it("does not submit a Cyrillic technical name", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByPlaceholderText("myVariable"), "температура");

    expect(screen.getByRole("button", { name: "Create" })).toBeDisabled();
    expect(screen.getByText(/Latin letter or underscore first/i)).toBeInTheDocument();
    expect(api.createVariable).not.toHaveBeenCalled();
  });
});
