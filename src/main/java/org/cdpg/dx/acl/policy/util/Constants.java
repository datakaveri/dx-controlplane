package org.cdpg.dx.acl.policy.util;

public class Constants {
  public static final String INSERT_USER_TABLE = "insert into user_table(_id,email_id,first_name,last_name) " +
      " values ($1,$2,$3,$4)" +
      " on conflict (_id) do update SET (email_id,first_name,last_name) " +
      "= (EXCLUDED.email_id,EXCLUDED.first_name,EXCLUDED.last_name) RETURNING _id";
}
