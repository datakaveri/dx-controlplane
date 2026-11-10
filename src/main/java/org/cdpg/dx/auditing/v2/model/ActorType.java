package org.cdpg.dx.auditing.v2.model;

/**
 * Identifies who actually performed the audited action.
 *
 * <ul>
 *   <li>{@link #SELF} - the primary account holder acting directly.
 *   <li>{@link #DELEGATE} - a delegate acting on behalf of the delegator (audit row's {@code
 *       userId} is the delegator; {@code delegateId} identifies the delegate).
 *   <li>{@link #APP} - an app credential acting on behalf of its owner (audit row's {@code
 *       appId} identifies the app).
 * </ul>
 */
public enum ActorType {
  SELF,
  DELEGATE,
  APP
}
