package com.johnlpage.mews.model;

/**
 *  This IS used to set whether we are using a simple replace ro a recursive
 *  field update to overwrite existing documents. RFU detects what has changed
 *  and records only that in transaction log so can be a lot smaller. It is also
 *  needed when doing "smarter" updates for example capturing history of changes
 *  within a single transactions.
 */
public enum UpdateStrategy {
  UPSERT,
  REPLACE,
  ;
}
