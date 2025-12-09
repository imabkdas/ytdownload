import React from 'react';

/**
 * DownloadForm Component - Form for URL input and download options
 * @param {Object} props - Component props
 * @param {string} props.url - Current URL value
 * @param {Function} props.onUrlChange - Callback when URL changes
 * @param {string} props.quality - Current quality selection
 * @param {Function} props.onQualityChange - Callback when quality changes
 * @param {Function} props.onDownload - Callback when download button is clicked
 * @param {boolean} props.loading - Loading state
 * @param {string} props.error - Error message
 * @param {Function} props.onErrorClear - Callback to clear error
 * @returns {JSX.Element}
 */
function DownloadForm({
    url,
    onUrlChange,
    quality,
    onQualityChange,
    onDownload,
    loading,
    error,
    onErrorClear,
}) {
    /**
     * Handle key press on URL input
     * @param {KeyboardEvent} e - Keyboard event
     */
    const handleKeyPress = (e) => {
        if (e.key === 'Enter' && !loading) {
            onDownload();
        }
    };

    /**
     * Handle URL input change
     * @param {Event} e - Input change event
     */
    const handleUrlInputChange = (e) => {
        onUrlChange(e.target.value);
        if (error) {
            onErrorClear();
        }
    };

    return (
        <>
            <div className="form-group">
                <div className="input-wrapper">
                    <svg
                        className="input-icon"
                        width="20"
                        height="20"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        aria-hidden="true"
                    >
                        <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
                    </svg>
                    <input
                        type="text"
                        className="url-input"
                        placeholder="Paste YouTube URL here..."
                        value={url}
                        onChange={handleUrlInputChange}
                        onKeyPress={handleKeyPress}
                        disabled={loading}
                        aria-label="YouTube URL input"
                        aria-invalid={!!error}
                        aria-describedby={error ? 'error-message' : undefined}
                    />
                </div>
                {error && (
                    <div className="error-message" id="error-message" role="alert">
                        {error}
                    </div>
                )}
            </div>

            <div className="options-group">
                <div className="select-wrapper">
                    <label htmlFor="quality-select">Quality</label>
                    <select
                        id="quality-select"
                        className="quality-select"
                        value={quality}
                        onChange={(e) => onQualityChange(e.target.value)}
                        disabled={loading}
                        aria-label="Download quality selection"
                    >
                        <option value="HQ">Highest Quality</option>
                        <option value="720">720p HD</option>
                        <option value="480">480p SD</option>
                        <option value="mp3">MP3 Audio</option>
                    </select>
                </div>
            </div>

            <button
                className="download-button"
                onClick={onDownload}
                disabled={loading || !url.trim()}
                aria-busy={loading}
                aria-label={loading ? 'Processing download' : 'Start download'}
            >
                {loading ? (
                    <>
                        <svg
                            className="button-spinner"
                            width="20"
                            height="20"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                            aria-hidden="true"
                        >
                            <path d="M21 12a9 9 0 11-6.219-8.56" />
                        </svg>
                        Processing...
                    </>
                ) : (
                    <>
                        <svg
                            width="20"
                            height="20"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                            aria-hidden="true"
                        >
                            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
                            <polyline points="7 10 12 15 17 10" />
                            <line x1="12" y1="15" x2="12" y2="3" />
                        </svg>
                        Download
                    </>
                )}
            </button>
        </>
    );
}

export default DownloadForm;
