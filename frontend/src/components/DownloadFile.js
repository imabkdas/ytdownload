import React, { useState } from 'react';
import axios from 'axios';

function DownloadFile() {
    const [url, setUrl] = useState('');
    const [format, setFormat] = useState('mp4');
    const [loading, setLoading] = useState(false);

    const handleDownload = async () => {
        setLoading(true);
        try {
            const response = await axios.post(`http://localhost:8080/api/${format}/download`, null, {
				params: { url, format },
				responseType: 'blob' // Important to handle the download
			});

			const blob = new Blob([response.data], { type: response.headers['content-type'] });

            const link = document.createElement('a');
            link.href = window.URL.createObjectURL(blob);
            link.download = `video.${format}`;
            link.click();
        } catch (error) {
            console.error('Error downloading video:', error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="App">
            <h1>YouTube Video Downloader</h1>
            <input
                type="text"
                placeholder="Enter YouTube URL"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
            />
            <select value={format} onChange={(e) => setFormat(e.target.value)}>
                <option value="mp4">MP4</option>
                <option value="mp3">MP3</option>
            </select>
            <button onClick={handleDownload} disabled={loading}>
                {loading ? 'Downloading...' : 'Download'}
            </button>
        </div>
    );
}

export default DownloadFile;
