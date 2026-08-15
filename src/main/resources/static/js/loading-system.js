/**
 * FAMS Loading System - JavaScript Utilities
 *
 * Professional spinner and skeleton loader management for enterprise-grade UX.
 * Provides programmatic control over loading states across the application.
 *
 * USAGE:
 *   Content Section Loading (PRIMARY METHOD):
 *   - Loading.showContentLoading(container, message, size) / Loading.hideContentLoading(container)
 *
 *   Traditional Methods:
 *   - Loading.showSpinner(container, options) / Loading.hideSpinner()
 *   - Loading.showOverlay(message) / Loading.hideOverlay() [Manual only, no auto-trigger]
 *   - Loading.showSkeleton(container, type) / Loading.hideSkeleton(container)
 *   - Loading.showCardSpinner(card) / Loading.hideCardSpinner(card)
 *   - Loading.showButtonSpinner(btn) / Loading.hideButtonSpinner(btn)
 *
 *   Skeleton Types:
 *   - 'metric-card': Single metric card skeleton
 *   - 'metric-grid': 4-card grid skeleton
 *   - 'chart-card': Chart card skeleton
 *   - 'table': Table rows skeleton
 *   - 'list': List items skeleton
 *   - 'form': Form fields skeleton
 *   - 'modal': Modal content skeleton
 *
 *   NOTE: Auto-overlay on page navigation has been disabled.
 *   Use showContentLoading() for data loading instead.
 */

(function(global) {
  'use strict';

  const SPINNER_PRIMARY = '#C0182A';
  const OVERLAY_Z = 9999;
  const TRANSITION_MS = 280;

  /**
   * Create a spinner element with given size and classes
   */
  function createSpinner(size = 'md', extraClasses = '') {
    const spinner = document.createElement('div');
    spinner.className = `spinner spinner-${size} ${extraClasses}`.trim();
    return spinner;
  }

  /**
   * Create a skeleton element with given variant
   */
  function createSkeleton(variant = 'text', extraClasses = '') {
    const skeleton = document.createElement('div');
    skeleton.className = `skeleton skeleton-${variant} ${extraClasses}`.trim();
    return skeleton;
  }

   /**
    * Core Loading namespace
    */
   const Loading = {
     /**
      * Show a centered spinner in a container
      */
     showSpinner(container, options = {}) {
       if (!container) return;
       const { size = 'md', message = '' } = options;
       container.classList.add('spinner-center');
       container.innerHTML = '';
       const spinner = createSpinner(size);
       if (message) {
         const wrapper = document.createElement('div');
         wrapper.style.textAlign = 'center';
         wrapper.appendChild(spinner);
         const p = document.createElement('p');
         p.style.marginTop = '16px';
         p.textContent = message;
         wrapper.appendChild(p);
         container.appendChild(wrapper);
       } else {
         container.appendChild(spinner);
       }
     },

     /**
      * Show professional loading state in content area
      * Only dims the content section, sidebar & header remain clear
      */
     showContentLoading(container, message = '', size = 'lg') {
       if (!container) return;

       // Create or get overlay
       let overlay = container.querySelector('.content-loading-overlay');
       if (!overlay) {
         overlay = document.createElement('div');
         overlay.className = 'content-loading-overlay';
         container.insertAdjacentElement('afterbegin', overlay);
       }

       // Create spinner wrapper
       const wrapper = document.createElement('div');
       wrapper.className = 'spinner-wrapper';

       const spinner = createSpinner(size);
       wrapper.appendChild(spinner);

       if (message) {
         const p = document.createElement('p');
         p.textContent = message;
         wrapper.appendChild(p);
       }

       overlay.innerHTML = '';
       overlay.appendChild(wrapper);
       overlay.classList.add('active');

       // Store state
       container.dataset.contentLoading = 'true';
       return overlay;
     },

     /**
      * Hide content loading overlay
      */
     hideContentLoading(container) {
       if (!container) return;
       const overlay = container.querySelector('.content-loading-overlay');
       if (overlay) {
         overlay.classList.remove('active');
         // Delay removal for smooth animation
         setTimeout(() => {
           if (overlay.parentNode) {
             overlay.remove();
           }
         }, 150);
       }
       delete container.dataset.contentLoading;
     },

    /**
     * Show a full-screen overlay spinner
     */
    showOverlay(message = 'Loading...') {
      let overlay = document.getElementById('fams-overlay-spinner');
      if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'fams-overlay-spinner';
        overlay.className = 'spinner-overlay';
        overlay.innerHTML = `
          <div class="spinner-content">
            <div class="spinner spinner-lg"></div>
            <p>Loading...</p>
          </div>
        `;
        document.body.appendChild(overlay);
      }
      overlay.querySelector('.spinner-content p').textContent = message;
      overlay.classList.add('active');
      return overlay;
    },

    hideOverlay() {
      const overlay = document.getElementById('fams-overlay-spinner');
      if (overlay) overlay.classList.remove('active');
    },

    /**
     * Show skeleton loader in a container by replacing content
     * Stores original content for restoration
     */
    showSkeleton(container, skeletonType = 'default') {
      if (!container) return;

      if (!container._realContent) {
        container._realContent = container.innerHTML;
      }

      const skeletonHtml = this.getSkeletonTemplate(skeletonType);
      container.innerHTML = skeletonHtml;
      container.classList.add('loading-active');
    },

    /**
     * Show skeleton loader without losing real content (uses data attribute)
     */
    showSkeletonSafe(container, skeletonType = 'default') {
      if (!container) return;
      const skeletonHtml = this.getSkeletonTemplate(skeletonType);

      // Create or show skeleton wrapper
      let skeletonWrap = container.querySelector('.skeleton-wrapper');
      if (!skeletonWrap) {
        skeletonWrap = document.createElement('div');
        skeletonWrap.className = 'skeleton-wrapper';
        container.insertBefore(skeletonWrap, container.firstChild);
      }
      skeletonWrap.innerHTML = skeletonHtml;
      skeletonWrap.style.display = 'block';
      container.dataset.skeletonShown = 'true';
    },

    /**
     * Hide skeleton loader and restore real content
     */
    hideSkeleton(container) {
      if (!container) return;

      // Remove skeleton wrapper if exists
      const skeletonWrap = container.querySelector('.skeleton-wrapper');
      if (skeletonWrap) skeletonWrap.style.display = 'none';

      if (container._realContent !== undefined) {
        container.innerHTML = container._realContent;
        delete container._realContent;
      }
      container.classList.remove('loading-active');
      delete container.dataset.skeletonShown;
    },

    /**
     * Show card spinner (top-right corner spinner for async refresh)
     */
    showCardSpinner(card) {
      if (!card) return;
      let spinner = card.querySelector('.card-spinner');
      if (!spinner) {
        spinner = document.createElement('div');
        spinner.className = 'card-spinner';
        spinner.innerHTML = '<div class="spinner"></div>';
        card.style.position = 'relative';
        card.appendChild(spinner);
      }
      spinner.classList.add('active');
      return spinner;
    },

    hideCardSpinner(card) {
      if (!card) return;
      const spinner = card.querySelector('.card-spinner');
      if (spinner) spinner.classList.remove('active');
    },

    /**
     * Show spinner inside a button during async submission
     */
    showButtonSpinner(btn, size = 'sm') {
      if (!btn) return;
      if (!btn._originalHtml) {
        btn._originalHtml = btn.innerHTML;
      }
      btn.classList.add('loading');
      btn.disabled = true;
      btn.innerHTML = `<span class="spinner spinner-${size}"></span>`;
    },

    hideButtonSpinner(btn) {
      if (!btn) return;
      btn.classList.remove('loading');
      btn.disabled = false;
      if (btn._originalHtml) {
        btn.innerHTML = btn._originalHtml;
        delete btn._originalHtml;
      }
    },

    /**
     * Show spinner for table row loading (e.g., "loading more" pagination)
     */
    showTableRowSpinner(row) {
      if (!row) return;
      row.classList.add('table-row-spinner', 'active');
      row.innerHTML = `<div class="spinner spinner-sm"></div><span style="margin-left: 12px;">Loading more...</span>`;
    },

    hideTableRowSpinner(row) {
      if (!row) return;
      row.classList.remove('table-row-spinner', 'active');
    },

    /**
     * Show skeleton for entire page (dashboard initial load)
     */
    showPageSkeleton(container, skeletonType = 'default') {
      if (!container) return;
      this.showSkeleton(container, skeletonType);
    },

    /**
     * Simulate page load with skeleton then reveal content
     */
    async simulatePageLoad(container, skeletonType = 'default', delay = 600) {
      this.showPageSkeleton(container, skeletonType);
      await new Promise(resolve => setTimeout(resolve, delay));
      this.hideSkeleton(container);
    },

    /**
     * Get skeleton template HTML for different types
     */
    getSkeletonTemplate(type) {
      switch (type) {
        case 'metric-card':
          return `
            <div class="skeleton-metric-card">
              <div class="skeleton-row">
                <div class="skeleton skeleton-icon"></div>
                <div class="skeleton skeleton-label"></div>
              </div>
              <div class="skeleton skeleton-value"></div>
            </div>`;

        case 'metric-grid':
          return `
            <div class="skeleton-dashboard-grid">
              ${Array(4).fill().map(() => `
                <div class="skeleton-dashboard-card">
                  <div class="skeleton-header">
                    <div class="skeleton skeleton-square-sm"></div>
                    <div class="skeleton skeleton-text-sm" style="width: 80px;"></div>
                  </div>
                  <div class="skeleton skeleton-title"></div>
                  <div class="skeleton skeleton-chart" style="height: 120px;"></div>
                </div>
              `).join('')}
            </div>`;

        case 'chart-card':
          return `
            <div class="skeleton-dashboard-card">
              <div class="skeleton-header">
                <div class="skeleton skeleton-title" style="width: 200px;"></div>
                <div class="skeleton skeleton-badge"></div>
              </div>
              <div class="skeleton skeleton-chart"></div>
            </div>`;

        case 'table':
          return `
            <div class="skeleton-list">
              ${Array(6).fill().map(() => `
                <div class="skeleton-table-row">
                  <div class="skeleton skeleton-circle-sm"></div>
                  <div class="skeleton skeleton-text"></div>
                  <div class="skeleton skeleton-text"></div>
                  <div class="skeleton skeleton-text"></div>
                  <div class="skeleton skeleton-badge"></div>
                  <div class="skeleton skeleton-badge"></div>
                  <div class="skeleton skeleton-badge"></div>
                  <div class="skeleton skeleton-badge"></div>
                  <div class="skeleton skeleton-badge"></div>
                </div>
              `).join('')}
            </div>`;

        case 'list':
          return `
            <div class="skeleton-list">
              ${Array(5).fill().map(() => `
                <div class="skeleton-list-item">
                  <div class="skeleton skeleton-avatar"></div>
                  <div class="skeleton-content">
                    <div class="skeleton skeleton-title"></div>
                    <div class="skeleton skeleton-meta"></div>
                  </div>
                </div>
              `).join('')}
            </div>`;

        case 'form':
          return `
            <div class="skeleton-form">
              <div class="skeleton-form-row">
                <div class="skeleton-form-group">
                  <div class="skeleton skeleton-form-label"></div>
                  <div class="skeleton skeleton-form-field"></div>
                </div>
                <div class="skeleton-form-group">
                  <div class="skeleton skeleton-form-label"></div>
                  <div class="skeleton skeleton-form-field"></div>
                </div>
              </div>
              <div class="skeleton-form-group">
                <div class="skeleton skeleton-form-label" style="width: 140px;"></div>
                <div class="skeleton skeleton-form-field"></div>
              </div>
              <div class="skeleton-form-group">
                <div class="skeleton skeleton-form-label" style="width: 120px;"></div>
                <div class="skeleton skeleton-form-field skeleton-form-field-textarea"></div>
              </div>
              <div class="skeleton-form-footer">
                <div class="skeleton skeleton-button"></div>
                <div class="skeleton skeleton-button" style="width: 90px;"></div>
              </div>
            </div>`;

        case 'modal':
          return `
            <div class="skeleton-modal">
              <div class="skeleton-header">
                <div class="skeleton skeleton-icon"></div>
                <div class="skeleton skeleton-title" style="width: 200px;"></div>
              </div>
              <div class="skeleton-body">
                ${Array(3).fill().map(() => `
                  <div class="skeleton-form-group">
                    <div class="skeleton skeleton-form-label" style="width: 100px;"></div>
                    <div class="skeleton skeleton-form-field"></div>
                  </div>
                `).join('')}
              </div>
              <div class="skeleton-footer">
                <div class="skeleton skeleton-button"></div>
                <div class="skeleton skeleton-button" style="width: 90px;"></div>
              </div>
            </div>`;

        case 'chart':
          return `<div class="skeleton-chart"></div>`;

        default:
          return `
            <div class="skeleton-dashboard-grid">
              ${Array(4).fill().map(() => `
                <div class="skeleton-dashboard-card">
                  <div class="skeleton-header">
                    <div class="skeleton skeleton-square-sm"></div>
                    <div class="skeleton skeleton-text-sm" style="width: 80px;"></div>
                  </div>
                  <div class="skeleton skeleton-title"></div>
                  <div class="skeleton skeleton-chart" style="height: 120px;"></div>
                </div>
              `).join('')}
            </div>`;
      }
    },

    /**
     * Helper to wrap an async operation with spinner display
     */
    async withSpinner(asyncFn, container, options = {}) {
      try {
        this.showSpinner(container, options);
        const result = await asyncFn();
        return result;
      } finally {
        // Container will be updated by the caller
      }
    },

    /**
     * Helper to wrap an async operation with skeleton display
     */
    async withSkeleton(asyncFn, container, skeletonType = 'default') {
      this.showSkeleton(container, skeletonType);
      try {
        const result = await asyncFn();
        this.hideSkeleton(container);
        return result;
      } catch (error) {
        this.hideSkeleton(container);
        throw error;
      }
    },

     /**
      * Advanced: Show content loading with skeleton placeholder
      * Combines overlay + skeleton in one call
      */
     async showContentLoadingWithSkeleton(container, skeletonType = 'table', message = 'Loading data...') {
       this.showContentLoading(container, message, 'lg');
       // Skeleton will be shown after a brief delay for smooth UX
       await new Promise(resolve => setTimeout(resolve, 200));
       return container;
     },

     /**
      * Hide content loading and show skeleton
      */
     hideContentLoadingShowSkeleton(container, skeletonType = 'table') {
       this.hideContentLoading(container);
       this.showSkeleton(container, skeletonType);
     },

      /**
       * Initialize automatic detection of loading states on page
       */
      init() {
        // Show overlay spinner on initial page load
        document.addEventListener('DOMContentLoaded', () => {
          // Mark page as loaded
          document.body.classList.remove('page-loading');
          this.hideOverlay();
        });

        // Intercept form submissions to show button spinner
        document.addEventListener('submit', (e) => {
          const form = e.target;
          const submitBtn = form.querySelector('button[type="submit"], input[type="submit"]');
          if (submitBtn && !submitBtn.disabled) {
            this.showButtonSpinner(submitBtn);
          }
        });

        // DISABLED: Auto-overlay on link navigation
        // Users can still manually call Loading.showOverlay() if needed
        // This prevents the full-screen spinner from appearing on every page access
        /*
        document.addEventListener('click', (e) => {
          const link = e.target.closest('a');
          if (link && link.href && !link.target && !link.hasAttribute('data-no-spinner')) {
            // Only show overlay for internal navigation
            if (link.href.startsWith(window.location.origin)) {
              this.showOverlay();
            }
          }
        });
        */
      }
  };

  // Auto-init on load
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => Loading.init());
  } else {
    Loading.init();
  }

  // Export to global scope
  global.Loading = Loading;

  // Also expose utility functions
  global.LoadingUtils = {
    createSpinner,
    createSkeleton,
    SPINNER_PRIMARY
  };

})(window);
