import React, { useState } from 'react';
import useFileDownload from '../hooks/useFileDownload';
import DownloadForm from './DownloadForm';
import ProgressBar from './ProgressBar';
import './DownloadFile.css';

/**
 * DownloadFile Component - Main component for YouTube video/audio downloads
 * Orchestrates the download process and manages the UI
 */
function DownloadFile() {
    const [url, setUrl] = useState('');
    const [format, setFormat] = useState('mp4');
    const [quality, setQuality] = useState('720');
    
    // Use custom hook for download logic
    const {
        loading,
        progress,
        status,
        error,
        downloadAudio,
        downloadVideo,
        cancelDownload,
        clearError,
    } = useFileDownload();

    /**
     * Handle download button click
     * Routes to audio or video download based on quality selection
     */
    const handleDownload = async () => {
        if (quality === 'mp3') {
            await downloadAudio(url);
        } else {
            await downloadVideo(url, format, quality);
        }
    };

    return (
        <div className="download-container">
            <div className="download-card">
                <div className="header">
                    <div className="logo">
                        <svg
                            width="48"
                            height="48"
                            viewBox="0 0 24 24"
                            fill="none"
                            xmlns="http://www.w3.org/2000/svg"
                            aria-hidden="true"
                        >
                            <path
                                d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"
                                fill="currentColor"
                            />
                        </svg>
                    </div>
                    <h1>YouTube Downloader</h1>
                    <p className="subtitle">Download videos and audio in high quality</p>
                </div>

                {/* Download Form Component */}
                <DownloadForm
                    url={url}
                    onUrlChange={setUrl}
                    quality={quality}
                    onQualityChange={setQuality}
                    onDownload={handleDownload}
                    loading={loading}
                    error={error}
                    onErrorClear={clearError}
                />

                {/* Progress Bar Component */}
                {loading && (
                    <ProgressBar
                        progress={progress}
                        status={status}
                        onCancel={cancelDownload}
                    />
                )}

                {/* Info Section */}
                <div className="info-section">
                    <p className="info-text">
                        <svg
                            width="16"
                            height="16"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                            aria-hidden="true"
                        >
                            <circle cx="12" cy="12" r="10" />
                            <line x1="12" y1="16" x2="12" y2="12" />
                            <line x1="12" y1="8" x2="12.01" y2="8" />
                        </svg>
                        Free, fast, and secure. No registration required.
                    </p>
                </div>
            </div>
        </div>
    );
}

export default DownloadFile;
