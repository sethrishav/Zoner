import React, { useState, useRef, useEffect } from 'react';
import { ChevronDown, Check } from 'lucide-react';

/**
 * CustomSelect - A modern, rich custom dropdown select component.
 * Replaces native <select> with a customizable list containing color dots, badges, and checkmarks.
 *
 * Props:
 * - value: string | number
 * - onChange: (value) => void
 * - options: Array<{ value, label, color?, badge?, icon?: ReactNode, description?: string }>
 * - placeholder?: string
 * - disabled?: boolean
 * - className?: string
 * - size?: 'sm' | 'md'
 * - align?: 'left' | 'right'
 * - renderOption?: (option, isSelected) => ReactNode
 */
export default function CustomSelect({
  value,
  onChange,
  options = [],
  placeholder = 'Select...',
  disabled = false,
  className = '',
  size = 'md',
  align = 'left',
  renderOption,
}) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef(null);

  // Close when clicking outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  // Close on Escape key
  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);

  const selectedOption = options.find((opt) => String(opt.value) === String(value));

  const handleSelect = (val) => {
    onChange(val);
    setIsOpen(false);
  };

  const isSmall = size === 'sm';

  return (
    <div ref={containerRef} className={`relative inline-block w-full ${className}`}>
      {/* Trigger Button */}
      <button
        type="button"
        disabled={disabled}
        onClick={() => !disabled && setIsOpen((prev) => !prev)}
        className={`w-full flex items-center justify-between gap-2 rounded-xl border text-left transition-all ${
          isOpen
            ? 'border-brand-500 ring-2 ring-brand-500/20 bg-white'
            : 'border-slate-200/90 bg-slate-50/70 hover:bg-white hover:border-slate-300'
        } ${
          isSmall ? 'py-2 px-2.5 text-xs' : 'py-2.5 px-3.5 text-xs'
        } ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer shadow-2xs'}`}
      >
        <div className="flex items-center gap-2 min-w-0 flex-1">
          {selectedOption?.color && (
            <span
              className="w-3 h-3 rounded-full shrink-0 shadow-xs border border-white"
              style={{ backgroundColor: selectedOption.color }}
            />
          )}
          {selectedOption?.icon && (
            <span className="shrink-0 text-slate-500 flex items-center">{selectedOption.icon}</span>
          )}
          <span className="truncate font-medium text-slate-800">
            {selectedOption ? selectedOption.label : <span className="text-slate-400">{placeholder}</span>}
          </span>
          {selectedOption?.badge && (
            <span className="shrink-0 text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-brand-50 text-brand-700 border border-brand-200/60">
              {selectedOption.badge}
            </span>
          )}
        </div>

        <ChevronDown
          className={`${isSmall ? 'w-3.5 h-3.5' : 'w-4 h-4'} text-slate-400 transition-transform duration-200 shrink-0 ${
            isOpen ? 'rotate-180 text-brand-600' : ''
          }`}
        />
      </button>

      {/* Dropdown Menu */}
      {isOpen && (
        <div
          className={`absolute z-50 mt-1.5 w-full min-w-[220px] max-h-64 overflow-y-auto rounded-2xl bg-white border border-slate-200 shadow-xl shadow-slate-900/10 py-1.5 focus:outline-none ${
            align === 'right' ? 'right-0' : 'left-0'
          }`}
          role="listbox"
        >
          {options.length === 0 ? (
            <div className="px-3.5 py-2.5 text-xs text-slate-400 text-center">
              No options available
            </div>
          ) : (
            options.map((option) => {
              const isSelected = String(option.value) === String(value);

              if (renderOption) {
                return (
                  <div
                    key={option.value}
                    onClick={() => handleSelect(option.value)}
                    className="cursor-pointer"
                  >
                    {renderOption(option, isSelected)}
                  </div>
                );
              }

              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => handleSelect(option.value)}
                  className={`w-full flex items-center justify-between gap-2.5 px-3.5 py-2.5 text-left text-xs transition-colors cursor-pointer ${
                    isSelected
                      ? 'bg-brand-50/70 font-semibold text-brand-900'
                      : 'text-slate-700 hover:bg-slate-50'
                  }`}
                  role="option"
                  aria-selected={isSelected}
                >
                  <div className="flex items-center gap-2.5 min-w-0 flex-1">
                    {option.color && (
                      <span
                        className="w-3 h-3 rounded-full shrink-0 shadow-xs border border-white"
                        style={{ backgroundColor: option.color }}
                      />
                    )}
                    {option.icon && (
                      <span className="shrink-0 text-slate-400">{option.icon}</span>
                    )}
                    <div className="flex flex-col min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="truncate">{option.label}</span>
                        {option.badge && (
                          <span className="shrink-0 text-[9px] font-semibold uppercase tracking-wider px-1.5 py-0.5 rounded-md bg-slate-100 text-slate-600">
                            {option.badge}
                          </span>
                        )}
                      </div>
                      {option.description && (
                        <span className="text-[11px] font-normal text-slate-400 truncate">
                          {option.description}
                        </span>
                      )}
                    </div>
                  </div>

                  {isSelected && (
                    <Check className="w-4 h-4 text-brand-600 shrink-0 ml-2" />
                  )}
                </button>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
