-- Snapshot the item's owning hierarchy node on each policy, mirroring
-- request.item_hierarchy_node_id (V89) -- lets dept_admins create, list and
-- deactivate policies for items owned by a department in their subtree.
ALTER TABLE policy
ADD COLUMN IF NOT EXISTS item_hierarchy_node_id UUID;

CREATE INDEX IF NOT EXISTS policy_item_hierarchy_node_idx
ON policy (item_organization_id, item_hierarchy_node_id);

-- Backfill policies created by approving an access request from that
-- request's snapshot. Policies created directly stay NULL (the node lives
-- only in the catalogue) and remain manageable by the owner/org_admin only.
UPDATE policy p
SET item_hierarchy_node_id = r.item_hierarchy_node_id
FROM request r
WHERE p.request_id = r.request_id
  AND p.item_hierarchy_node_id IS NULL
  AND r.item_hierarchy_node_id IS NOT NULL;
