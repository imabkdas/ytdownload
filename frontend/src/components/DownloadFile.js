import React, { useState } from 'react';
import axios from 'axios';

function DownloadFile() {
    const [url, setUrl] = useState('');
    const [format, setFormat] = useState('mp4');
    const [quality, setQuality] = useState('720');
    const [loading, setLoading] = useState(false);

    const handleDownload = async () => {
        setLoading(true);
        try {
            if(quality === "mp3"){
                const response = await axios.post("http://localhost:8080/api/audio/download", null, {
                    params: { url, format},
                    responseType: 'blob' // Important to handle the download
                });
    
                const blob = new Blob([response.data], { type: response.headers['content-type'] });
    
                const link = document.createElement('a');
                link.href = window.URL.createObjectURL(blob);
                // link.download = `audio.${format}`;
                link.download = "audio.mp3";
                link.click();
            }
            else {
                const response = await axios.post("http://localhost:8080/api/video/download", null, {
                    params: { url, format, quality },
                    responseType: 'blob' // Important to handle the download
                });
    
                const blob = new Blob([response.data], { type: response.headers['content-type'] });
    
                const link = document.createElement('a');
                link.href = window.URL.createObjectURL(blob);
                link.download = `video.${format}`;
                link.click();
            }
            
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
            <select value={quality} onChange={(e) => setQuality(e.target.value)}>
                <option value="HQ">HQ</option>
                <option value="720">720p</option>
                <option value="480">480p</option>
                <option value="mp3">MP3</option>
            </select>
            <button onClick={handleDownload} disabled={loading}>
                {loading ? 'Downloading...' : 'Download'}
            </button>
        </div>
    );
}

export default DownloadFile;
