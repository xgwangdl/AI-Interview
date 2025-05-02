import React, { useRef, useState } from 'react';
import Webcam from 'react-webcam';
import { Dialog } from '@vaadin/react-components/Dialog';
import { ClipLoader } from 'react-spinners';

export const config: ViewConfig = {
  menu: {
    exclude: true,
  },
};

const FaceVerificationLogin = ({ userId, onVerificationSuccess }) => {
  const webcamRef = useRef(null);
  const [image, setImage] = useState(null);
  const [isDialogOpen, setIsDialogOpen] = useState(true);
  const [isLoading, setIsLoading] = useState(false);

  const capture = () => {
    const imageSrc = webcamRef.current.getScreenshot();
    setImage(imageSrc);
    setIsLoading(true);
    fetch(`/LoginAdmin`, {
      method: 'POST',
      body: JSON.stringify({ image: imageSrc, userId: userId }),
      headers: {
        'Content-Type': 'application/json',
      },
    })
      .then(response => response.json())
      .then(data => {
        onVerificationSuccess(data);
        setIsDialogOpen(false);
      })
      .catch(error => {
        alert('人脸识别失败，请重试。');
      })
      .finally(() => {
        setIsLoading(false);
      });
  };

  const handleDialogClose = (e) => {
    // 判断是否是点击外部区域触发的关闭
    if (e.detail.source === 'outside') {
      e.preventDefault(); // 阻止关闭
    } else {
      setIsDialogOpen(false); // 其他情况允许关闭
    }
  };

  return (
    <Dialog
      opened={isDialogOpen}
      onOpenedChanged={handleDialogClose} // 监听关闭事件
      noCloseOnOutsideClick // 禁用点击外部关闭
      style={{
        padding: '20px',
        borderRadius: '15px',
        maxWidth: '400px',
        boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)',
        backgroundColor: '#f9f9f9',
      }}
    >
      <div style={{ textAlign: 'center' }}>
        <h2 style={{ marginBottom: '10px', color: '#333', fontSize: '20px', fontWeight: 'bold' }}>
          人脸识别认证
        </h2>
        <p style={{ marginBottom: '15px', color: '#666', fontSize: '14px' }}>
          请确保您的脸部清晰可见，并点击“拍照”按钮进行认证。
        </p>
        <Webcam
          audio={false}
          ref={webcamRef}
          screenshotFormat="image/jpeg"
          style={{
            width: '100%',
            height: '300px',
            borderRadius: '10px',
            marginBottom: '15px',
            border: '2px solid #ddd',
          }}
        />
        <button
          onClick={capture}
          style={{
            padding: '8px 16px',
            fontSize: '14px',
            borderRadius: '5px',
            border: 'none',
            backgroundColor: '#4CAF50',
            color: 'white',
            cursor: 'pointer',
            transition: 'background-color 0.3s ease',
          }}
          disabled={isLoading}
        >
          {isLoading ? '认证中...' : '拍照'}
        </button>
        {isLoading && (
          <div style={{ marginTop: '15px' }}>
            <ClipLoader color="#4CAF50" size={25} />
            <p style={{ marginTop: '8px', color: '#666', fontSize: '12px' }}>
              正在验证，请稍候...
            </p>
          </div>
        )}
      </div>
    </Dialog>
  );
};

export default FaceVerificationLogin;