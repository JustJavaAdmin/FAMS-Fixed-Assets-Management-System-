(function () {
    'use strict';

    const initialized = new WeakSet();
    const openWrappers = new Set();
    let globalsBound = false;

    function closeWrapper(wrapper) {
        const trigger = wrapper.querySelector('.searchable-select-trigger');
        const dropdown = wrapper.querySelector('.searchable-select-dropdown');
        if (!trigger || !dropdown) return;

        dropdown.classList.remove('open');
        trigger.classList.remove('open');
        trigger.setAttribute('aria-expanded', 'false');
        openWrappers.delete(wrapper);
    }

    function closeAllWrappers(except) {
        document.querySelectorAll('.searchable-select-wrapper').forEach(wrapper => {
            if (wrapper !== except) {
                closeWrapper(wrapper);
            }
        });
    }

    function bindGlobals() {
        if (globalsBound) return;
        globalsBound = true;

        document.addEventListener('click', function (e) {
            openWrappers.forEach(wrapper => {
                if (!wrapper.contains(e.target)) {
                    closeWrapper(wrapper);
                }
            });
        });

        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            openWrappers.forEach(closeWrapper);
        });

        window.addEventListener('scroll', function () {
            openWrappers.forEach(closeWrapper);
        }, true);

        window.addEventListener('resize', function () {
            openWrappers.forEach(closeWrapper);
        });
    }

    function initSearchableSelect(wrapper) {
        if (!wrapper || initialized.has(wrapper)) return;

        const select = wrapper.querySelector('select');
        if (!select) return;

        initialized.add(wrapper);
        bindGlobals();

        select.style.display = 'none';

        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'searchable-select-trigger';
        trigger.setAttribute('role', 'combobox');
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');

        const textSpan = document.createElement('span');
        textSpan.className = 'trigger-text';

        const arrowSpan = document.createElement('span');
        arrowSpan.className = 'trigger-arrow material-symbols-outlined';
        arrowSpan.textContent = 'expand_more';

        trigger.appendChild(textSpan);
        trigger.appendChild(arrowSpan);

        const dropdown = document.createElement('div');
        dropdown.className = 'searchable-select-dropdown';

        const searchBox = document.createElement('div');
        searchBox.className = 'search-box';

        const searchInput = document.createElement('input');
        searchInput.type = 'text';
        searchInput.placeholder = 'Search...';
        searchInput.setAttribute('aria-label', 'Search options');

        searchBox.appendChild(searchInput);

        const optionsList = document.createElement('div');
        optionsList.className = 'options-list';
        optionsList.setAttribute('role', 'listbox');

        dropdown.appendChild(searchBox);
        dropdown.appendChild(optionsList);
        wrapper.appendChild(trigger);
        wrapper.appendChild(dropdown);

        function updateTriggerText() {
            const selected = select.options[select.selectedIndex];
            textSpan.textContent = selected ? selected.text : select.options[0]?.text || 'Select...';
        }

        function syncSelectedState() {
            optionsList.querySelectorAll('.option-item').forEach(item => {
                const isSelected = Number(item.dataset.index) === select.selectedIndex;
                item.classList.toggle('selected', isSelected);
                item.setAttribute('aria-selected', isSelected ? 'true' : 'false');
            });
        }

        function renderOptions(filter = '') {
            optionsList.innerHTML = '';
            const lowerFilter = filter.toLowerCase();
            let found = false;

            Array.from(select.options).forEach((opt, index) => {
                const text = opt.text || '';
                if (filter && !text.toLowerCase().includes(lowerFilter)) {
                    return;
                }

                found = true;
                const item = document.createElement('div');
                item.className = 'option-item' + (opt.selected ? ' selected' : '') + (opt.disabled ? ' disabled' : '');
                item.textContent = text;
                item.dataset.index = String(index);

                if (!opt.disabled) {
                    item.setAttribute('role', 'option');
                    item.setAttribute('aria-selected', opt.selected ? 'true' : 'false');
                    item.addEventListener('click', function () {
                        select.selectedIndex = Number(this.dataset.index);
                        select.dispatchEvent(new Event('change', { bubbles: true }));
                        closeWrapper(wrapper);
                        updateTriggerText();
                    });
                    item.addEventListener('mouseenter', function () {
                        optionsList.querySelectorAll('.option-item').forEach(el => el.classList.remove('highlight'));
                        this.classList.add('highlight');
                    });
                }

                optionsList.appendChild(item);
            });

            if (!found) {
                const noResult = document.createElement('div');
                noResult.className = 'no-results';
                noResult.textContent = 'No options found';
                optionsList.appendChild(noResult);
            }
        }

        function openDropdown() {
            closeAllWrappers(wrapper);
            dropdown.classList.add('open');
            trigger.classList.add('open');
            trigger.setAttribute('aria-expanded', 'true');
            searchInput.value = '';
            renderOptions('');
            searchInput.focus();
            const selectedItem = optionsList.querySelector('.option-item.selected');
            if (selectedItem) {
                selectedItem.scrollIntoView({ block: 'nearest' });
            }
            openWrappers.add(wrapper);
        }

        trigger.addEventListener('click', function (e) {
            e.stopPropagation();
            if (dropdown.classList.contains('open')) {
                closeWrapper(wrapper);
            } else {
                openDropdown();
            }
        });

        trigger.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                closeWrapper(wrapper);
                return;
            }
            if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
                e.preventDefault();
                openDropdown();
            }
        });

        searchInput.addEventListener('input', function () {
            renderOptions(this.value);
        });

        searchInput.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                closeWrapper(wrapper);
                trigger.focus();
            }
        });

        select.addEventListener('change', function () {
            updateTriggerText();
            syncSelectedState();
            if (dropdown.classList.contains('open')) {
                renderOptions(searchInput.value);
            }
        });

        const observer = new MutationObserver(function () {
            updateTriggerText();
            if (dropdown.classList.contains('open')) {
                renderOptions(searchInput.value);
            }
        });
        observer.observe(select, { childList: true, subtree: false });

        updateTriggerText();
        renderOptions('');
    }

    function initAll(root) {
        (root || document).querySelectorAll('.searchable-select-wrapper').forEach(initSearchableSelect);
    }

    document.addEventListener('DOMContentLoaded', function () {
        initAll(document);
    });

    window.initSearchableSelects = initAll;
})();
