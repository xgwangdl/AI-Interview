/** 服务器明确拒绝的录像（格式/大小/鉴权）重试同一文件没有意义；超时和限流仍可稍后补传。 */
export function isPermanentUploadRejection(error) {
  const status = Number(error?.status);
  return status >= 400 && status < 500 && status !== 408 && status !== 429;
}

export function uploadRejectionMessage(error, fallback = '录制文件不完整，请重新录制。') {
  return `视频无法保存：${error?.message || fallback}`;
}
