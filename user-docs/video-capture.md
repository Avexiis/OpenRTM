# Video Capture

[All guides](README.md) | No console connection required

Video Capture previews a connected capture device and can save MP4 recordings and PNG screenshots. Device discovery and preview begin automatically when the page is created.

## Choose a Source

Select the video device and use **Refresh devices** after changing hardware. With no incoming signal, the preview shows **No input source detected** and waits for the signal to return.

| Setting | Choices |
| --- | --- |
| Resolution | Source resolution, 1080p, or 720p |
| Frame rate | Source FPS, 60 FPS, or 30 FPS |
| Decoder | Automatic, FFmpeg, or OpenCV |
| Performance | Lowest latency, High frame rate, or Compatibility |

Source resolution leaves the incoming size unchanged. The other sizes request that capture mode from the device. Resizing the preview does not request a higher source resolution.

Lowest latency reduces buffering. High frame rate favors faster capture modes, including MJPEG where available. Compatibility allows more buffering. Automatic tries available decoders; select one directly when troubleshooting.

Choose an audio input separately. **Monitor audio** plays it through the computer during preview; **No audio** disables capture audio. The audio status appears beside the capture actions.

## Viewer and Saved Media

- **Detached viewer** opens a separate video window.
- **Lock viewer size** fixes that window to the selected resolution; clearing it allows resizing.
- **Record** starts an MP4; **Stop recording** finishes it. Available audio is included.
- **Copy recording to clipboard** also places the finished recording on the clipboard for pasting into an app that accepts files.
- **Screenshot** saves the current frame as a PNG.

The preview stays at 16:9. Choose the capture folder before recording or taking screenshots. Source choices, capture settings, folder, and viewer options are remembered.

If capture cannot start, the rest of OpenRTM remains usable. Check the device driver, close other apps using the device, refresh the list, and try a different decoder or Compatibility mode.
