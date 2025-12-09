import { useState, useCallback, useRef } from 'react';
import axios from 'axios';

/**
 * Custom hook for handling file downloads with progress tracking and abort capability
 * @returns {Object} Download state and methods
 */
export const useFileDownload = () => {
    const [loading, setLoading] = useState(false);
    const [progress, setProgress] = useState(0);
    const [status, setStatus] = useState('');
    const [error, setError] = useState('');
    
    // Track the current abort controller for cancellation
    const abortControllerRef = useRef(null);
    
    // Interval for simulating progress
    const progressIntervalRef = useRef(null);

    /**
     * Simulate progress during processing phase
     * @param {Function} setProgressFn - State setter for progress
     * @returns {number} Interval ID
     */
    const simulateProgress = useCallback((setProgressFn) => {
        let currentProgress = 0;
        
        return setInterval(() => {
            if (currentProgress < 95) {
                currentProgress += Math.random() * 2;
                if (currentProgress > 95) currentProgress = 95;
                setProgressFn(Math.floor(currentProgress));
            }
        }, 800);
    }, []);

    /**
     * Handle downloading audio file from backend
     * @param {string} url - YouTube URL
     * @returns {Promise<void>}
     */
    const downloadAudio = useCallback(async (url) => {
        if (!url.trim()) {
            setError('Please enter a YouTube URL');
            return;
        }

        setLoading(true);
        setProgress(0);
        setError('');
        setStatus('Extracting audio from video...');

        // Create new abort controller for this request
        abortControllerRef.current = new AbortController();
        let hasRealProgress = false;

        try {
            progressIntervalRef.current = simulateProgress(setProgress);

            const response = await axios.post(
                `${process.env.REACT_APP_API_BASE_URL}/api/audio/download`,
                null,
                {
                    params: { url, format: 'mp3' },
                    responseType: 'blob',
                    signal: abortControllerRef.current.signal,
                    onDownloadProgress: (progressEvent) => {
                        if (!hasRealProgress) {
                            hasRealProgress = true;
                            clearInterval(progressIntervalRef.current);
                        }

                        if (progressEvent.lengthComputable && progressEvent.total) {
                            const percentCompleted = Math.round(
                                (progressEvent.loaded * 100) / progressEvent.total
                            );
                            // Map to 50-100% range (processing done, now downloading)
                            const downloadProgress =
                                50 + Math.round((percentCompleted * 50) / 100);
                            setProgress(downloadProgress);
                        } else if (progressEvent.loaded) {
                            // Estimate progress based on loaded bytes
                            const estimatedProgress = 50 +
                                Math.min(
                                    Math.round((progressEvent.loaded / 10000000) * 50),
                                    50
                                );
                            setProgress(estimatedProgress);
                        }
                    }
                }
            );

            clearInterval(progressIntervalRef.current);
            setStatus('Preparing file...');
            setProgress(98);

            // Create blob and trigger download
            const blob = new Blob([response.data], {
                type: response.headers['content-type'],
            });
            triggerDownload(blob, 'audio.mp3');

            setProgress(100);
            setStatus('Download complete!');

            // Reset after 2 seconds
            setTimeout(() => {
                setLoading(false);
                setProgress(0);
                setStatus('');
            }, 2000);
        } catch (err) {
            if (err.name === 'AbortError') {
                setError('Download cancelled');
            } else {
                console.error('Error downloading audio:', err);
                setError(
                    err.response?.data?.message ||
                    err.message ||
                    'An error occurred while downloading. Please try again.'
                );
            }
            setLoading(false);
            setProgress(0);
            setStatus('');
            clearInterval(progressIntervalRef.current);
        }
    }, [simulateProgress]);

    /**
     * Handle downloading video file from backend
     * @param {string} url - YouTube URL
     * @param {string} format - Video format (mp4, webm, etc.)
     * @param {string} quality - Video quality (HQ, 720, 480, mp3)
     * @returns {Promise<void>}
     */
    const downloadVideo = useCallback(async (url, format, quality) => {
        if (!url.trim()) {
            setError('Please enter a YouTube URL');
            return;
        }

        setLoading(true);
        setProgress(0);
        setError('');
        setStatus('Processing video (this may take a minute)...');

        // Create new abort controller for this request
        abortControllerRef.current = new AbortController();
        let hasRealProgress = false;

        try {
            progressIntervalRef.current = simulateProgress(setProgress);

            const response = await axios.post(
                `${process.env.REACT_APP_API_BASE_URL}/api/video/download`,
                null,
                {
                    params: { url, format, quality },
                    responseType: 'blob',
                    signal: abortControllerRef.current.signal,
                    onDownloadProgress: (progressEvent) => {
                        if (!hasRealProgress) {
                            hasRealProgress = true;
                            clearInterval(progressIntervalRef.current);
                        }

                        if (progressEvent.lengthComputable && progressEvent.total) {
                            const percentCompleted = Math.round(
                                (progressEvent.loaded * 100) / progressEvent.total
                            );
                            // Map to 50-100% range (processing done, now downloading)
                            const downloadProgress =
                                50 + Math.round((percentCompleted * 50) / 100);
                            setProgress(downloadProgress);
                        } else if (progressEvent.loaded) {
                            // Estimate progress based on loaded bytes (assuming average video size)
                            const estimatedProgress = 50 +
                                Math.min(
                                    Math.round((progressEvent.loaded / 50000000) * 50),
                                    50
                                );
                            setProgress(estimatedProgress);
                        }
                    }
                }
            );

            clearInterval(progressIntervalRef.current);
            setStatus('Preparing file...');
            setProgress(98);

            // Create blob and trigger download
            const blob = new Blob([response.data], {
                type: response.headers['content-type'],
            });
            triggerDownload(blob, `video.${format}`);

            setProgress(100);
            setStatus('Download complete!');

            // Reset after 2 seconds
            setTimeout(() => {
                setLoading(false);
                setProgress(0);
                setStatus('');
            }, 2000);
        } catch (err) {
            if (err.name === 'AbortError') {
                setError('Download cancelled');
            } else {
                console.error('Error downloading video:', err);
                setError(
                    err.response?.data?.message ||
                    err.message ||
                    'An error occurred while downloading. Please try again.'
                );
            }
            setLoading(false);
            setProgress(0);
            setStatus('');
            clearInterval(progressIntervalRef.current);
        }
    }, [simulateProgress]);

    /**
     * Cancel the current download
     */
    const cancelDownload = useCallback(() => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
            clearInterval(progressIntervalRef.current);
            setLoading(false);
            setProgress(0);
            setStatus('');
        }
    }, []);

    /**
     * Trigger browser download for a blob
     * @param {Blob} blob - File blob to download
     * @param {string} filename - Name for the downloaded file
     */
    const triggerDownload = (blob, filename) => {
        const link = document.createElement('a');
        link.href = window.URL.createObjectURL(blob);
        link.download = filename;
        link.click();
        window.URL.revokeObjectURL(link.href);
    };

    /**
     * Clear error message
     */
    const clearError = useCallback(() => {
        setError('');
    }, []);

    return {
        loading,
        progress,
        status,
        error,
        downloadAudio,
        downloadVideo,
        cancelDownload,
        clearError,
    };
};

export default useFileDownload;
