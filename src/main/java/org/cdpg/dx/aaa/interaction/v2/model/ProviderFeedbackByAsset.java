package org.cdpg.dx.aaa.interaction.v2.model;

import java.util.List;
import java.util.UUID;

public record ProviderFeedbackByAsset(UUID assetId, List<ProviderFeedbackEntry> feedback) {}
