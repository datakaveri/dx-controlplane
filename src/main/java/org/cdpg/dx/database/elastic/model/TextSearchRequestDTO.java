package org.cdpg.dx.database.elastic.model;

public record TextSearchRequestDTO(String q, Boolean fuzzy, Boolean autoComplete) {}
