import { useState } from 'react';
import { Maximize, Minimize } from 'react-feather';

const HeaderRight = () => {
    const [isFullScreen, setIsFullScreen] = useState(false);



    const toggleFullScreen = () => {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen();
            setIsFullScreen(true);
        } else {
            document.exitFullscreen();
            setIsFullScreen(false);
        }
    };

    return (
        <div className="d-flex align-items-center">


            <button className="btn btn-link text-secondary p-2 me-3 d-none d-sm-block" onClick={toggleFullScreen}>
                {isFullScreen ? <Minimize size={20} /> : <Maximize size={20} />}
            </button>


            <div className="dropdown me-1">
                <button className="btn btn-link text-secondary p-2" data-bs-toggle="dropdown">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" width="24" height="24" stroke-width="2"> <path d="M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2 -3v-3a7 7 0 0 1 4 -6"></path> <path d="M9 17v1a3 3 0 0 0 6 0v-1"></path> </svg> 
                    <span className="position-absolute top-0 start-100 translate-middle badge rounded-pill" style={{ backgroundColor: '#f6952c', fontSize: '0.7em' }}>
                        10
                    </span>
                </button>
            </div>
        </div>
    );
};

export default HeaderRight;