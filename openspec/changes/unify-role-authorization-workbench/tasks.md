## 1. Workbench shell

- [x] 1.1 Create the canonical role-and-authorization workbench with searchable role tree, create-role action, URL-backed role selection, and responsive master-detail layout
- [x] 1.2 Add the nine stable permission-aware tabs and lazy role-scoped content composition

## 2. Capability migration

- [x] 2.1 Integrate role basic information and page-capability authorization into the workbench
- [x] 2.2 Integrate business grants, data permissions, field rules, lowcode bundles, guards, decision preview, and diagnostics using existing owner APIs/components

## 3. Navigation and verification

- [x] 3.1 Replace the three menu entries with one workbench entry and add non-menu legacy redirects with tab deep links
- [x] 3.2 Verify URL restoration, permission-aware tab visibility, adaptive tables, pagination, type safety, and OpenSpec validity
- [x] 3.3 Publish the canonical workbench through the backend-driven menu projection and hide the superseded menu entries
- [x] 3.4 Keep role and authorization tab switching inside one application page without creating top-level application tabs
- [x] 3.5 Remove nested page shells from embedded role and data-permission content

## 4. Workbench hardening

- [x] 4.1 Replace the embedded legacy role page with a native basic-information detail and create/edit drawer
- [x] 4.2 Harden all authorization tabs against missing role/context data and fix runtime errors
- [x] 4.3 Unify spacing, height chains, scrolling, empty states, and responsive layout across the workbench

## 5. Adaptive tabular content

- [x] 5.1 Add a reusable client-paginated table frame matching the user-management table structure
- [x] 5.2 Migrate authorization child-tab tables to explicit fixed footer pagination and adaptive internal scrolling
- [x] 5.3 Verify type safety, pagination visibility, width overflow, and workbench height behavior

## 6. Lowcode authorization availability

- [x] 6.1 Route lowcode authorization publication APIs to service-lowcode through the platform gateway
- [x] 6.2 Degrade publication history loading to an inline warning instead of failing the whole tab
- [x] 6.3 Verify frontend type safety, gateway configuration, and OpenSpec validity

## 7. Non-lowcode field governance

- [x] 7.1 Register field metadata for built-in USER and ORG_UNIT business resources
- [x] 7.2 Group field-policy resources into fixed business and lowcode resources
- [x] 7.3 Verify migration safety, frontend types, and OpenSpec validity
