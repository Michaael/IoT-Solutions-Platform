package com.ispf.server.ai.agent;

/**
 * System-prompt section when the user attached a text spec/TZ file this turn.
 */
public final class AgentAttachmentPromptSection {

    private AgentAttachmentPromptSection() {
    }

    public static String forTextAttachments() {
        return """
                
                ## TEXT ATTACHMENT (this turn)
                
                The user attached a specification/TZ file (content is in the user message).
                - Do NOT paste the full TZ prose into summary — decompose into specBrief.functionalRequirements[].
                - Turn 1: discovery tools. Turn 2 (BOOTSTRAP): specBrief + ground_truth + intent_scope.
                - Extract FR-* from implicit phrases; each FR needs sourcePhrase from the attachment.
                - Phased sections across turns; SYNTHESIS enriches thin sections before approval.
                """;
    }
}
