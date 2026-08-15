# Content Section Loading Guide

## Overview

The improved loading system now provides **content-focused loading states** where:
- ✅ Only the **content section** is dimmed and shows loading spinner
- ✅ **Sidebar and header remain clear and visible**
- ✅ Simple, centered spinner with optional message
- ✅ Professional blur effect and smooth animations
- ✅ Sidebar and header remain interactive (optional)

## Quick Start

### Method 1: Simple Content Loading (Recommended)

Show a spinner that dims only the content area:

```javascript
// Get your content container (usually .main-content-area or a specific section)
const contentContainer = document.querySelector('.main-content-area');

// Show loading
Loading.showContentLoading(contentContainer, 'Loading data...', 'lg');

// Later, hide loading
Loading.hideContentLoading(contentContainer);
```

### Method 2: Content Loading with Skeleton

Show loading overlay first, then switch to skeleton loaders:

```javascript
const contentContainer = document.querySelector('.main-content-area');

// Show initial loading with spinner
await Loading.showContentLoadingWithSkeleton(contentContainer, 'table', 'Fetching records...');

// After data starts arriving, skeleton will show
// Then when data is ready:
Loading.hideSkeleton(contentContainer);
```

### Method 3: Manual Skeleton + Overlay Control

For more control:

```javascript
const contentContainer = document.querySelector('.main-content-area');

// Show content loading overlay
Loading.showContentLoading(contentContainer, 'Loading...', 'lg');

// Do async work
const data = await fetchData();

// Hide overlay and show skeleton
Loading.hideContentLoading(contentContainer);
Loading.showSkeleton(contentContainer, 'table');

// When data is ready
renderContent(data);
Loading.hideSkeleton(contentContainer);
```

## API Reference

### `Loading.showContentLoading(container, message, size)`

Display a professional loading overlay in the content section.

**Parameters:**
- `container` (HTMLElement): The container to show loading in (usually `.main-content-area`)
- `message` (string, optional): Loading message to display below spinner. Default: `''`
- `size` (string, optional): Spinner size - `'sm'`, `'md'`, `'lg'`, `'xl'`. Default: `'lg'`

**Returns:** The overlay element

**Example:**
```javascript
const overlay = Loading.showContentLoading(
  document.querySelector('.main-content-area'),
  'Refreshing dashboard...',
  'lg'
);
```

### `Loading.hideContentLoading(container)`

Remove the loading overlay and restore content interaction.

**Parameters:**
- `container` (HTMLElement): The container to hide loading from

**Example:**
```javascript
Loading.hideContentLoading(document.querySelector('.main-content-area'));
```

### `Loading.showContentLoadingWithSkeleton(container, skeletonType, message)`

Show content loading overlay and prepare skeleton placeholders.

**Parameters:**
- `container` (HTMLElement): The container element
- `skeletonType` (string): Type of skeleton - `'table'`, `'metric-grid'`, `'chart-card'`, etc.
- `message` (string, optional): Loading message

**Skeleton Types:**
- `'table'`: 6 table row skeletons
- `'metric-card'`: Single metric card
- `'metric-grid'`: 4-card grid
- `'chart-card'`: Chart placeholder
- `'list'`: 5 list item skeletons
- `'form'`: Form fields skeleton
- `'modal'`: Modal content skeleton

**Example:**
```javascript
await Loading.showContentLoadingWithSkeleton(
  contentContainer,
  'table',
  'Loading asset list...'
);
```

## CSS Classes

### `.content-loading-overlay`
Applied to the overlay div that appears during loading. Automatically added by JavaScript.

### `.spinner-wrapper`
Contains the spinner and message text. Automatically managed.

## Real-World Example: Dashboard Data Refresh

```javascript
// Simulate refreshing dashboard metrics
async function refreshDashboardMetrics() {
  const contentContainer = document.querySelector('.main-content-area');
  
  try {
    // Show loading in content area
    Loading.showContentLoading(
      contentContainer,
      'Refreshing dashboard metrics...',
      'lg'
    );
    
    // Simulate API call
    const response = await fetch('/api/dashboard/metrics');
    const data = await response.json();
    
    // Hide loading
    Loading.hideContentLoading(contentContainer);
    
    // Update UI with new data
    updateMetrics(data);
    
  } catch (error) {
    Loading.hideContentLoading(contentContainer);
    showErrorMessage('Failed to refresh metrics');
  }
}

// Call it
refreshDashboardMetrics();
```

## Real-World Example: Table Data Loading

```javascript
async function loadAssetTable() {
  const table = document.querySelector('#assets-table');
  const container = table.parentElement;
  
  try {
    // Show skeleton loaders for table
    Loading.showSkeleton(container, 'table');
    
    // Fetch data
    const response = await fetch('/api/assets');
    const assets = await response.json();
    
    // Render data
    renderAssets(assets);
    
    // Hide skeleton to reveal content
    Loading.hideSkeleton(container);
    
  } catch (error) {
    Loading.hideSkeleton(container);
    showErrorMessage('Failed to load assets');
  }
}
```

## Styling Guide

### Colors Used
- **Overlay Background**: `rgba(255, 255, 255, 0.72)` - Semi-transparent white
- **Blur Effect**: `3px` backdrop blur
- **Spinner Color**: `#C0182A` (brand red)
- **Text Color**: `#5d5e65` (secondary text)

### Customization

To customize the overlay opacity, edit `loading-system.css`:

```css
/* More transparent (less dimmed) */
.content-loading-overlay {
  background: rgba(255, 255, 255, 0.55);
}

/* More opaque (darker) */
.content-loading-overlay {
  background: rgba(255, 255, 255, 0.85);
}
```

To change blur intensity:

```css
.content-loading-overlay {
  backdrop-filter: blur(5px); /* More blur */
  backdrop-filter: blur(2px); /* Less blur */
}
```

## Best Practices

1. **Always hide loading after operation completes** - Use try/finally:
   ```javascript
   try {
     Loading.showContentLoading(container);
     const data = await fetchData();
     updateUI(data);
   } finally {
     Loading.hideContentLoading(container);
   }
   ```

2. **Use appropriate spinner sizes**:
   - `'sm'`: For small sections
   - `'md'`: For cards or small containers
   - `'lg'`: For full content area (default)
   - `'xl'`: For large sections

3. **Provide loading messages** - Helps users understand what's happening:
   ```javascript
   Loading.showContentLoading(container, 'Calculating depreciation...', 'lg');
   ```

4. **Combine with skeletons** - Show skeleton after spinner for better UX:
   ```javascript
   Loading.showContentLoading(container, 'Loading...');
   await delay(500);
   Loading.hideContentLoading(container);
   Loading.showSkeleton(container, 'table');
   ```

5. **Test on different screen sizes** - Ensure sidebar remains visible

## Browser Support

- ✅ Chrome/Edge 88+
- ✅ Firefox 85+
- ✅ Safari 15+
- ✅ Mobile browsers (iOS Safari, Chrome Mobile)

> Note: Requires CSS `backdrop-filter` support for blur effect. Fallback uses solid overlay.

## Troubleshooting

### Loading overlay not appearing
- Ensure container has `position: relative` (will be set automatically)
- Check z-index conflicts with other elements
- Verify `Loading` object is initialized

### Blur effect not working
- Check browser support for `backdrop-filter`
- Verify CSS isn't being overridden
- Check browser DevTools for CSS errors

### Content still interactive during loading
- This is by design - overlay allows some interaction
- To prevent all interaction, add `pointer-events: none` to content

### Loading stays visible
- Ensure `Loading.hideContentLoading()` is called
- Check console for JavaScript errors
- Verify overlay element is properly removed

## Migration Guide

### From Full-Screen Overlay to Content Loading

**Before:**
```javascript
Loading.showOverlay('Loading...');
// ... do work ...
Loading.hideOverlay();
```

**After:**
```javascript
Loading.showContentLoading(contentContainer, 'Loading...', 'lg');
// ... do work ...
Loading.hideContentLoading(contentContainer);
```

This keeps your sidebar and header visible and interactive!

---

For more information, see `loading-system.js` and `loading-system.css`.

