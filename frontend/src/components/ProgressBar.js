import React from 'react';
import '../components/ProgressBar.css';

/**
 * ProgressBar Component - Displays download progress with status message
 * @param {Object} props - Component props
 * @param {number} props.progress - Progress percentage (0-100)
 * @param {string} props.status - Status message to display
 * @param {Function} props.onCancel - Callback when cancel button is clicked
 * @returns {JSX.Element}
 */
function ProgressBar({ progress, status, onCancel }) {
    return (
        <div className="progress-section">
            <div className="status-text">{status}</div>
            <div
                className="progress-bar-container"
                data-progress={`${progress}%`}
                role="progressbar"
                aria-valuenow={progress}
                aria-valuemin="0"
                aria-valuemax="100"
                aria-label="Download progress"
            >
                <div
                    className="progress-bar"
                    style={{ width: `${Math.max(progress, 2)}%` }}
                >
                    {progress >= 15 && (
                        <span className="progress-text">{progress}%</span>
                    )}
                </div>
            </div>
            <div className="spinner-container">
                <div className="spinner"></div>
            </div>
            {onCancel && (
                <button className="cancel-button" onClick={onCancel}>
                    Cancel Download
                </button>
            )}
        </div>
    );
}

export default ProgressBar;
