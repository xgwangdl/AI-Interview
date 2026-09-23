let activeStream;

export function setActiveStream(stream) {
  activeStream = stream;
}

export function getActiveStream() {
  return activeStream;
}

export function stopActiveStream() {
  activeStream?.getTracks().forEach((track) => track.stop());
  activeStream = undefined;
}

export function supportedRecorderType() {
  if (typeof MediaRecorder === 'undefined') return '';
  return ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'].find((type) => MediaRecorder.isTypeSupported(type)) || '';
}
