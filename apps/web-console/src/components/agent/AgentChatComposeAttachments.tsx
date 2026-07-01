import { useCallback, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { AiProviderStatus } from "../../api/ai";
import {
  createAttachmentFromFile,
  providerAcceptAttribute,
  providerAllowsTextAttachments,
  providerAllowsImages,
  revokeAttachmentPreviews,
  type AgentChatAttachment,
} from "../../utils/agentChatAttachments";

export interface AgentChatComposeAttachmentsProps {
  provider: AiProviderStatus | undefined;
  attachments: AgentChatAttachment[];
  onChange: (next: AgentChatAttachment[]) => void;
  disabled?: boolean;
  onReject?: (reason: "unsupported" | "vision-not-supported") => void;
}

export function useAgentChatAttachments(
  attachments: AgentChatAttachment[],
  onChange: (next: AgentChatAttachment[]) => void
) {
  const addFiles = useCallback(
    (files: FileList | File[], provider: AiProviderStatus | undefined, onReject?: (reason: "unsupported" | "vision-not-supported") => void) => {
      const next = [...attachments];
      for (const file of Array.from(files)) {
        const created = createAttachmentFromFile(file, provider);
        if (created === "unsupported" || created === "vision-not-supported") {
          onReject?.(created);
          continue;
        }
        if (!next.some((item) => item.name === created.name && item.file.size === created.file.size)) {
          next.push(created);
        }
      }
      onChange(next);
    },
    [attachments, onChange]
  );

  const removeAttachment = useCallback(
    (id: string) => {
      const removed = attachments.find((item) => item.id === id);
      if (removed?.previewUrl) {
        URL.revokeObjectURL(removed.previewUrl);
      }
      onChange(attachments.filter((item) => item.id !== id));
    },
    [attachments, onChange]
  );

  const clearAttachments = useCallback(() => {
    revokeAttachmentPreviews(attachments);
    onChange([]);
  }, [attachments, onChange]);

  return { addFiles, removeAttachment, clearAttachments };
}

export default function AgentChatComposeAttachments({
  provider,
  attachments,
  onChange,
  disabled,
  onReject,
}: AgentChatComposeAttachmentsProps) {
  const { t } = useTranslation("ai");
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const { addFiles, removeAttachment } = useAgentChatAttachments(attachments, onChange);

  const canAttachText = providerAllowsTextAttachments(provider);
  const canAttachImages = providerAllowsImages(provider);
  const canAttachAnything = canAttachText || canAttachImages;
  const accept = providerAcceptAttribute(provider);

  const handleFiles = useCallback(
    (files: FileList | File[] | null | undefined) => {
      if (!files || files.length === 0 || disabled || !canAttachAnything) {
        return;
      }
      addFiles(files, provider, onReject);
    },
    [addFiles, canAttachAnything, disabled, onReject, provider]
  );

  if (!canAttachAnything) {
    return null;
  }

  return (
    <div className="ai-agent-compose-attachments">
      {attachments.length > 0 && (
        <ul className="ai-agent-attachment-chips" aria-label={t("agent.attachments.listAria")}>
          {attachments.map((item) => (
            <li key={item.id} className={`ai-agent-attachment-chip kind-${item.kind}`}>
              {item.kind === "image" && item.previewUrl ? (
                <img src={item.previewUrl} alt="" className="ai-agent-attachment-thumb" />
              ) : (
                <span className="ai-agent-attachment-icon" aria-hidden>
                  📄
                </span>
              )}
              <span className="ai-agent-attachment-name" title={item.name}>
                {item.name}
              </span>
              <button
                type="button"
                className="ai-agent-attachment-remove"
                aria-label={t("agent.attachments.remove", { name: item.name })}
                disabled={disabled}
                onClick={() => removeAttachment(item.id)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}

      <div
        className={`ai-agent-attach-row${dragOver ? " drag-over" : ""}`}
        onDragEnter={(event) => {
          event.preventDefault();
          if (!disabled) {
            setDragOver(true);
          }
        }}
        onDragOver={(event) => {
          event.preventDefault();
        }}
        onDragLeave={(event) => {
          if (event.currentTarget.contains(event.relatedTarget as Node)) {
            return;
          }
          setDragOver(false);
        }}
        onDrop={(event) => {
          event.preventDefault();
          setDragOver(false);
          handleFiles(event.dataTransfer.files);
        }}
      >
        <button
          type="button"
          className="btn small ai-agent-attach-btn"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
          title={t("agent.attachments.attachTitle")}
        >
          {t("agent.attachments.attach")}
        </button>
        <input
          ref={inputRef}
          type="file"
          className="ai-agent-attach-input"
          multiple
          accept={accept}
          disabled={disabled}
          onChange={(event) => {
            handleFiles(event.target.files);
            event.target.value = "";
          }}
        />
        <span className="op-muted ai-agent-attach-hint">
          {canAttachImages ? t("agent.attachments.hintWithImages") : t("agent.attachments.hintTextOnly")}
        </span>
      </div>
    </div>
  );
}

export function AgentMessageAttachmentPreview({
  attachments,
}: {
  attachments?: Array<{ name?: string; kind?: string; mimeType?: string }>;
}) {
  if (!attachments || attachments.length === 0) {
    return null;
  }
  return (
    <ul className="ai-agent-message-attachments">
      {attachments.map((item) => (
        <li key={`${item.kind ?? "file"}:${item.name}`} className="ai-agent-message-attachment">
          <span aria-hidden>{item.kind === "image" ? "🖼" : "📄"}</span>
          <span>{item.name}</span>
        </li>
      ))}
    </ul>
  );
}
