# Content Loading - Quick Reference Card

## Most Common Use Cases

### 1. Load Data with Spinner
```javascript
const container = document.querySelector('.main-content-area');

Loading.showContentLoading(container, 'Loading...', 'lg');
// ... fetch data ...
Loading.hideContentLoading(container);
```

### 2. Load Table Data
```javascript
const container = document.querySelector('.main-content-area');

Loading.showSkeleton(container, 'table');
// ... fetch data ...
Loading.hideSkeleton(container);
```

### 3. Load Dashboard Cards
```javascript
const container = document.querySelector('.main-content-area');

Loading.showSkeleton(container, 'metric-grid');
// ... fetch metrics ...
Loading.hideSkeleton(container);
```

### 4. Refresh with Loading Message
```javascript
const container = document.querySelector('.main-content-area');

Loading.showContentLoading(
  container,
  'Refreshing dashboard...',
  'lg'
);

// ... async work ...

Loading.hideContentLoading(container);
```

---

## Size Options
- `'sm'` - Small (14px)
- `'md'` - Medium (28px) 
- `'lg'` - Large (40px) ← Default for content area
- `'xl'` - Extra Large (56px)

---

## Skeleton Types
| Type | Size | Use For |
|------|------|---------|
| `'table'` | 6 rows | Asset list, transactions |
| `'metric-grid'` | 4 cards | Dashboard metrics |
| `'metric-card'` | 1 card | Single metric |
| `'chart-card'` | 1 card | Chart loading |
| `'list'` | 5 items | Activity, maintenance |
| `'form'` | Fields | Registration forms |
| `'modal'` | Content | Dialog boxes |

---

## Complete Example

```javascript
async function loadAssets() {
  const container = document.querySelector('.main-content-area');
  
  try {
    // Show loading overlay
    Loading.showContentLoading(
      container,
      'Fetching assets...',
      'lg'
    );
    
    // Fetch data
    const response = await fetch('/api/assets');
    const assets = await response.json();
    
    // Hide loading
    Loading.hideContentLoading(container);
    
    // Update UI
    renderAssets(assets);
    
  } catch (error) {
    Loading.hideContentLoading(container);
    console.error('Failed to load assets:', error);
  }
}
```

---

## CSS Classes (Auto-Applied)

```css
.content-loading-overlay  /* The dimmed overlay */
.spinner-wrapper          /* Spinner + message container */
```

---

## Key Features

✅ Only content area is dimmed
✅ Sidebar & header stay visible
✅ Smooth animations (300ms)
✅ Works on all screen sizes
✅ Professional appearance
✅ Easy to customize

---

## Pro Tips

1. **Always use try/finally:**
   ```javascript
   try {
     Loading.showContentLoading(container);
     // ... work ...
   } finally {
     Loading.hideContentLoading(container);
   }
   ```

2. **Cache containers:**
   ```javascript
   const contentContainer = document.querySelector('.main-content-area');
   // Reuse this reference
   ```

3. **Add meaningful messages:**
   ```javascript
   Loading.showContentLoading(
     container,
     'Calculating depreciation...',
     'lg'
   );
   ```

---

## What's Different from `showOverlay()`?

| Feature | showOverlay() | showContentLoading() |
|---------|---------------|---------------------|
| Covers | Full screen | Content area only |
| Sidebar | Blocked | Visible ✅ |
| Header | Blocked | Visible ✅ |
| Use Case | Page transitions | Data loading |
| Professional | Good | Better ✅ |

---

## Customization

### Less Dimmed
Edit `loading-system.css`:
```css
.content-loading-overlay {
  background: rgba(255, 255, 255, 0.55);
}
```

### More Blur
```css
.content-loading-overlay {
  backdrop-filter: blur(5px);
}
```

### Different Text Color
```css
.content-loading-overlay .spinner-wrapper p {
  color: #your-color;
}
```

---

## Troubleshooting

**Overlay not showing?**
- Check container selector
- Verify `Loading` is initialized
- Check browser console

**Spinner keeps spinning?**
- Make sure `hideContentLoading()` is called
- Check for errors in try/catch

**Sidebar not visible?**
- Verify container doesn't span full page
- Check CSS z-index

---

## Files Modified

- ✏️ `static/css/loading-system.css` - Added overlay styles
- ✏️ `static/js/loading-system.js` - Added new methods
- 📄 `CONTENT_LOADING_GUIDE.md` - Full documentation
- 📄 `templates/loading-examples.html` - Interactive demo

---

**Version:** 2.0
**Last Updated:** August 15, 2026
**Status:** Ready for Production ✅

