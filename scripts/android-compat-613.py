"""Run only the remaining API-specific compatibility checks against the existing APK."""
import subprocess,runpy
api=int(subprocess.check_output(['adb','shell','getprop','ro.build.version.sdk']).strip())
if api==28:
    runpy.run_path('scripts/android-reading-tools-smoke.py',run_name='__main__')
elif api==29:
    runpy.run_path('scripts/android-browser-smoke.py',run_name='__main__')
    runpy.run_path('scripts/android-local-download-smoke.py',run_name='__main__')
else:
    raise SystemExit('This compatibility suite is limited to API 28 and 29')
