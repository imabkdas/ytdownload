import React, { useState } from 'react';
import Home from './components/Home';
import DownloadFile from './components/DownloadFile';
import ErrorBoundary from './components/ErrorBoundary';
import { BrowserRouter, Route, Routes } from 'react-router-dom';

function App() {

    return (
        <ErrorBoundary>
            <BrowserRouter>
                <Routes>
                    <Route exact path='/home' element={<DownloadFile />} />
                    <Route exact path='/' element={<DownloadFile />} />
                    <Route exact path='/download' element={<DownloadFile />} />
                    <Route path='*' element={<Home />}/>
                </Routes>
            </BrowserRouter>
        </ErrorBoundary>
    );
}

export default App;
