package org.cdpg.dx.database.postgres.models;

public record UpsertResult<T>(T entity, boolean created) {}
