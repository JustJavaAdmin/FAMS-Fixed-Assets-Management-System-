# Asset Request Workflow - Quick Setup & Usage Guide

## Installation Steps

### 1. Database Setup
The `asset_requests` table will be created automatically by Spring Data JPA on application startup.

If you need to manually create it:
```sql
CREATE TABLE asset_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    requested_by_name VARCHAR(120) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reason LONGTEXT,
    approval_notes LONGTEXT,
    requested_at DATETIME NOT NULL,
    approved_at DATETIME,
    approved_by VARCHAR(120),
    approved_by_name VARCHAR(120),
    FOREIGN KEY (asset_id) REFERENCES assets(id),
    INDEX idx_status (status),
    INDEX idx_requested_by (requested_by),
    INDEX idx_asset_id (asset_id)
);
```

### 2. Compile and Deploy
```bash
./mvnw clean package
./mvnw spring-boot:run
```

## User Workflows

### For Employees

#### 1. Request an Asset
1. Navigate to **"Request Assets"** in sidebar (under employee dashboard)
2. View **Available Assets** section on the left
3. Browse unassigned assets with their categories and prices
4. Click **"Request"** button on desired asset
5. Modal appears - optionally add reason for the request
6. Click **"Submit Request"**
7. Asset appears in **"My Requests"** section as PENDING

#### 2. Track Requests
- View **"My Requests"** section to see all requests
- See status: PENDING (yellow), APPROVED (green), REJECTED (red)
- For APPROVED requests: Asset now appears in "My Assets"
- For REJECTED requests: View reason provided by manager
- Can **CANCEL** pending requests at any time

#### 3. Return an Asset
(Existing functionality)
1. Go to **"Returns"** in sidebar
2. Select asset to return
3. Provide return reason
4. Submit for approval
5. Asset manager will review and approve
6. Once approved, asset returns to "Asset Store"

### For Asset Managers (Admins)

#### 1. View Pending Requests
1. Navigate to **"Asset Requests"** in admin sidebar
2. See all pending requests in card grid layout
3. Counter badge shows number of pending requests
4. Each card shows:
   - Asset name and code
   - Employee who requested it
   - Category and request date
   - Request reason

#### 2. Approve Request
1. Click **"Approve"** button on the request card
2. Modal appears with asset and employee details
3. (Optional) Add approval notes
4. Click **"Approve Request"**
5. Asset is immediately assigned to the employee
6. Success message appears
7. Request moves from pending queue

#### 3. Reject Request
1. Click **"Reject"** button on the request card
2. Modal appears with asset and employee details
3. **Required**: Provide reason for rejection
4. Click **"Reject Request"**
5. Success message appears
6. Employee sees rejection reason in their request history

## Features Overview

### Asset Availability Filter
- Only **unassigned** assets appear (custodian field is empty)
- **Disposed** assets are excluded
- Updated in real-time as assets are assigned

### Request Status Lifecycle
```
PENDING → APPROVED (asset assigned to employee)
PENDING → REJECTED (with reason provided)
PENDING → CANCELLED (by employee)
```

### Audit Trail
Every request records:
- Who requested it (username & display name)
- When it was requested
- Who approved/rejected it (username & display name)
- When the decision was made
- Notes or reason for decision

### Validation & Safety
- Employees **cannot** request already-assigned assets
- Employees **cannot** have duplicate pending requests for same asset
- Disposed assets **cannot** be requested
- Manager can only approve if asset is still unassigned (prevents race conditions)

## Browser Compatibility

- ✅ Chrome/Edge (latest)
- ✅ Firefox (latest)
- ✅ Safari (latest)
- ✅ Mobile browsers (responsive design)

## Screenshots & UI Elements

### Employee Asset Requests Page
```
┌─────────────────────────┬─────────────────────────┐
│  Available Assets       │   My Requests           │
├─────────────────────────┼─────────────────────────┤
│ ┌─────────────────────┐ │ ┌─────────────────────┐ │
│ │ Asset Name          │ │ │ Asset Name          │ │
│ │ Category: IT        │ │ │ Status: PENDING     │ │
│ │ Price: ₦50,000      │ │ │ Requested: Jan 26   │ │
│ │ [Request] Button    │ │ │ [Cancel] Button     │ │
│ └─────────────────────┘ │ └─────────────────────┘ │
│                         │                         │
└─────────────────────────┴─────────────────────────┘
```

### Admin Asset Requests Page
```
Asset Requests [5 Pending]

┌──────────────────────────────────────┐
│ Dell Laptop                          │
│ Requested by: John Doe              │
│ Category: IT Equipment              │
│ Code: AST-2024-001                 │
│                                      │
│ "Need for project development"      │
│                                      │
│ [Approve] [Reject] Buttons          │
└──────────────────────────────────────┘
```

## Common Issues & Solutions

### Issue: "Asset is already assigned to someone"
**Solution**: This asset is already assigned. Choose another unassigned asset from the list.

### Issue: "You already have a pending request for this asset"
**Solution**: You already requested this asset. Either wait for approval or cancel your current request first.

### Issue: "Asset has already been assigned to someone else"
**Solution**: This occurs if two managers approve the same asset simultaneously. Another manager approved it first. No action needed.

### Issue: Can't see Asset Requests link in sidebar
**Solution**: 
- Verify you're logged in as an employee (for employee page)
- Verify you're logged in as an admin (for admin page)
- Clear browser cache and refresh

## API Reference for Developers

### Employee Endpoints
```http
GET /employee/asset-requests
- Returns: asset-requests.html page with available assets and my requests
- Requires: Employee role

POST /employee/asset-requests
- Body: { assetId: 123, reason: "..." }
- Returns: Redirect to /employee/asset-requests
- Requires: Employee role

POST /employee/asset-requests/{requestId}/cancel
- Returns: Redirect to /employee/asset-requests
- Requires: Employee role
```

### Admin Endpoints
```http
GET /admin/asset-requests
- Returns: admin asset requests approval page
- Requires: Admin role

POST /admin/asset-requests/{requestId}/approve
- Body: { notes: "..." }
- Returns: Redirect to /admin/asset-requests
- Requires: Admin role

POST /admin/asset-requests/{requestId}/reject
- Body: { notes: "..." } (required)
- Returns: Redirect to /admin/asset-requests
- Requires: Admin role
```

## Performance Considerations

- Asset list filters on **unassigned status only** (single field index)
- Request queries filtered by **status and username** (compound index)
- Modal dialogs are lightweight and load instantly
- No N+1 queries - Asset relationship is eagerly loaded

## Next Steps

1. ✅ Test employee request workflow
2. ✅ Test admin approval workflow
3. ✅ Verify return functionality works
4. ✅ Test on mobile devices
5. Consider sending email notifications
6. Monitor request metrics/analytics

