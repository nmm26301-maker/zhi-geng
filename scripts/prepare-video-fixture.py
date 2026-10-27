"""Generate original moving colour/video+tone fixtures, never shipped in release APKs."""
from pathlib import Path
import subprocess
base=Path('video-fixture');base.mkdir(exist_ok=True)
subprocess.run(['ffmpeg','-v','error','-y','-f','lavfi','-i','testsrc2=size=480x270:rate=24','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t','24','-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p','-crf','25','-g','48','-c:a','aac','-b:a','64k','-movflags','+faststart',str(base/'clip.mp4')],check=True)
subprocess.run(['ffmpeg','-v','error','-y','-i',str(base/'clip.mp4'),'-c','copy','-hls_time','2','-hls_playlist_type','vod','-hls_segment_filename',str(base/'segment-%03d.ts'),str(base/'master.m3u8')],check=True)
print('Original video fixture:',(base/'clip.mp4').stat().st_size,'bytes',flush=True)
