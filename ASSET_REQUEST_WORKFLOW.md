# Asset Request Workflow Implementation - Complete Guide

## Overview
Implemented a complete asset request workflow allowing employees to request unassigned assets, with asset managers able to approve or reject requests. When approved, assets are automatically assigned to the requesting employee.

## Architecture & Components

### 1. Database Models

#### AssetRequest Entity (`AssetRequest.java`)
- **Table**: `asset_requests`
- **Fields**:
  - `id` (Long, Primary Key)
  - `asset` (ManyToOne relationship with Asset)
  - `requestedBy` (String) - Employee username
  - `requestedByName` (String) - Display name
  - `status` (Enum: PENDING, APPROVED, REJECTED, CANCELLED)
  - `reason` (String) - Why employee needs the asset
  - `approvalNotes` (String) - Notes from approver
  - `requestedAt` (LocalDateTime) - When request was made
  - `approvedAt` (LocalDateTime) - When it was approved/rejected
  - `approvedBy` (String) - Approver username
  - `approvedByName` (String) - Approver display name

### 2. Data Access Layer

#### AssetRequestRepository (`AssetRequestRepository.java`)
JPA Repository with custom finder methods:
- `findByStatusOrderByRequestedAtDesc(status)` - Get requests by status
- `findByRequestedByOrderByRequestedAtDesc(username)` - Get employee's requests
- `findByAssetAndStatusOrderByRequestedAtDesc(asset, status)` - Get requests for specific asset
- `findByRequestedByAndStatusOrderByRequestedAtDesc(username, status)` - Get employee's pending requests
- `countByStatus(status)` - Count requests by status

### 3. Business Logic Layer

#### AssetRequestService (`AssetRequestService.java`)
Core service with methods:

**For Employees:**
- `requestAsset(assetId, username, displayName, reason)` - Submit asset request
- `getAvailableAssets()` - Get list of unassigned, non-disposed assets
- `getMyRequests(username)` - Get all requests from current employee
- `getMyPendingRequests(username)` - Get pending requests
- `cancelRequest(requestId)` - Cancel a pending request

**For Asset Managers:**
- `getPendingRequests()` - Get all pending asset requests
- `approveRequest(requestId, approverId, approverName, notes)` - Approve and assign asset
- `rejectRequest(requestId, rejecterId, rejecterName, notes)` - Reject request

**Utility:**
- `countPendingRequests()` - Count pending requests for dashboard

### 4. Controllers

#### EmployeeController Updates
New endpoints:
- `GET /employee/asset-requests` - View available assets and my requests
- `POST /employee/asset-requests` - Submit new asset request
- `POST /employee/asset-requests/{requestId}/cancel` - Cancel pending request

#### AdminController Updates
New endpoints:
- `GET /admin/asset-requests` - View all pending requests for approval
- `POST /admin/asset-requests/{requestId}/approve` - Approve and assign asset
- `POST /admin/asset-requests/{requestId}/reject` - Reject request

### 5. User Interfaces

#### Employee Asset Requests Page (`employee/asset-requests.html`)
Two-column layout:
1. **Left Column - Available Assets**
   - Grid of unassigned assets
   - Shows category and purchase cost
   - "Request" button for each asset
   
2. **Right Column - My Requests**
   - Displays all employee's requests (PENDING, APPROVED, REJECTED)
   - Shows request date, status, and notes
   - Cancel button for pending requests
   - Approval/rejection details when available

**Features:**
- Modal dialog for requesting assets with reason field
- Clean status indicators with color coding
- Responsive design
- Loading and error states

#### Admin Asset Requests Approval Page (`admin/asset-requests.html`)
Grid-based card layout:
- Shows all pending requests
- Displays:
  - Asset name and code
  - Employee who requested it
  - Category and request date
  - Request reason
  - Action buttons (Approve/Reject)

**Features:**
- Separate modals for approve/reject actions
- Optional approval notes
- Required rejection reason
- Real-time feedback with success/error messages
- Pending request counter badge

### 6. Navigation Integration

#### Employee Layout (`employee-layout.html`)
Added "Request Assets" link in sidebar after "My Assets":
- Icon: request_page
- Path: /employee/asset-requests

#### Admin Layout (`admin-layout.html`)
Added "Asset Requests" link in General section:
- Icon: pending_actions
- Path: /admin/asset-requests

## Workflow

### Asset Request Flow

```
1. Employee views available unassigned assets
   ↓
2. Employee clicks "Request" button for desired asset
   ↓
3. Modal appears - employee provides reason (optional)
   ↓
4. Request submitted to database with PENDING status
   ↓
5. Asset Manager sees pending request in approval queue
   ↓
6. Asset Manager can:
   a) APPROVE → Asset assigned to employee, request marked APPROVED
   b) REJECT → Request marked REJECTED with reason
   ↓
7. Employee sees request status updated
   ↓
8. If APPROVED, asset appears in employee's "My Assets"
```

### Asset Return Flow (Existing)
The existing return functionality from `EmployeeController` continues to work:
- Employee can request return from their assigned assets
- Returns an asset to "Asset Store" via lifecycle workflow
- Requires asset manager approval

## Key Features

### 1. Business Logic Validation
- ✅ Only unassigned, non-disposed assets can be requested
- ✅ Prevents duplicate pending requests for same asset by same employee
- ✅ Validates asset is still unassigned when approving (prevents race conditions)
- ✅ Only pending requests can be approved/rejected
- ✅ Can only cancel pending requests

### 2. Error Handling
- Asset not found errors
- Asset already assigned errors
- Duplicate request prevention
- Status validation
- User authorization checks

### 3. User Experience
- Real-time loading states
- Clear success/error messages
- Modal dialogs for confirmations
- Color-coded status indicators
- Responsive design for all screen sizes

### 4. Data Integrity
- Foreign key constraints (asset_id)
- Required fields validation
- Proper transaction handling
- Automatic timestamp management

## API Endpoints Summary

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | /employee/asset-requests | Employee | View available assets & my requests |
| POST | /employee/asset-requests | Employee | Submit new asset request |
| POST | /employee/asset-requests/{id}/cancel | Employee | Cancel pending request |
| GET | /admin/asset-requests | Admin | View pending requests for approval |
| POST | /admin/asset-requests/{id}/approve | Admin | Approve & assign asset |
| POST | /admin/asset-requests/{id}/reject | Admin | Reject request with reason |

## Database Migration

Run the following SQL to create the `asset_requests` table:

```sql
CREATE TABLE asset_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_id BIGINT NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    requested_by_name VARCHAR(120) NOT NULL,
    status VARCHAR(50) NOT NULL,
    reason TEXT,
    approval_notes TEXT,
    requested_at DATETIME NOT NULL,
    approved_at DATETIME,
    approved_by VARCHAR(120),
    approved_by_name VARCHAR(120),
    FOREIGN KEY (asset_id) REFERENCES assets(id)
);

CREATE INDEX idx_asset_requests_status ON asset_requests(status);
CREATE INDEX idx_asset_requests_requested_by ON asset_requests(requested_by);
CREATE INDEX idx_asset_requests_asset_id ON asset_requests(asset_id);
```

## Testing Checklist

- [ ] Employee can view available unassigned assets
- [ ] Employee can request an asset with reason
- [ ] Preventing duplicate pending requests for same asset
- [ ] Employee can see their requests with status
- [ ] Employee can cancel pending requests
- [ ] Asset manager sees pending requests
- [ ] Asset manager can approve request and asset is assigned
- [ ] Asset manager can reject request with reason
- [ ] Approved assets appear in employee's assigned assets
- [ ] Return functionality still works for assigned assets
- [ ] Navigation links work correctly in both layouts
- [ ] Responsive design on mobile devices

## Future Enhancements

1. **Email Notifications** - Notify employees when request is approved/rejected
2. **Request History** - Archive of all requests with filtering
3. **Bulk Requests** - Allow requesting multiple assets at once
4. **Request Comments** - Back-and-forth communication in request
5. **Expiration** - Auto-cancel old pending requests
6. **Availability Calendar** - Show asset availability by date
7. **Analytics** - Report on request approval rates and trends
8. **Integration with Checkout** - Auto-create checkout record on approval

## Security Considerations

- ✅ Employees can only see their own requests (controller enforces this)
- ✅ Only authenticated users can request assets
- ✅ Asset managers (admins) can approve requests via role-based access
- ✅ Cannot assign disposed or already-assigned assets
- ✅ Usernames and display names stored for audit trail

