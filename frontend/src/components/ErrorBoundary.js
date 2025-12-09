import React from 'react';
import './ErrorBoundary.css';

class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            hasError: false,
            error: null,
            errorInfo: null,
            errorCount: 0
        };
    }

    static getDerivedStateFromError(error) {
        // Update state so the next render will show the fallback UI
        return { hasError: true };
    }

    componentDidCatch(error, errorInfo) {
        // Log error details for debugging
        console.error('ErrorBoundary caught an error:', error, errorInfo);
        
        this.setState(prevState => ({
            error: error,
            errorInfo: errorInfo,
            errorCount: prevState.errorCount + 1
        }));

        // Log to external error reporting service if needed
        // Example: logErrorToService(error, errorInfo);
    }

    handleReset = () => {
        this.setState({
            hasError: false,
            error: null,
            errorInfo: null
        });
    };

    render() {
        if (this.state.hasError) {
            return (
                <div className="error-boundary-container">
                    <div className="error-boundary-content">
                        <div className="error-icon">⚠️</div>
                        <h1 className="error-title">Something went wrong</h1>
                        <p className="error-message">
                            We're sorry for the inconvenience. An unexpected error occurred while processing your request.
                        </p>
                        
                        {process.env.NODE_ENV === 'development' && (
                            <div className="error-details">
                                <h2 className="error-details-title">Development Details:</h2>
                                <details className="error-stack">
                                    <summary>Error Information</summary>
                                    <pre className="error-code">
                                        {this.state.error && this.state.error.toString()}
                                        {'\n\n'}
                                        {this.state.errorInfo && this.state.errorInfo.componentStack}
                                    </pre>
                                </details>
                                <p className="error-count">
                                    Total errors caught: {this.state.errorCount}
                                </p>
                            </div>
                        )}

                        <div className="error-actions">
                            <button 
                                className="error-reset-btn" 
                                onClick={this.handleReset}
                            >
                                Try Again
                            </button>
                            <button 
                                className="error-home-btn" 
                                onClick={() => window.location.href = '/'}
                            >
                                Back to Home
                            </button>
                        </div>

                        <p className="error-footer">
                            If the problem persists, please try refreshing the page or contact support.
                        </p>
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
