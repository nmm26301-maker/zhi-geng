"""Pinned official TypeScript compiler and license; verify the complete npm tarball first."""
import base64,hashlib,io,pathlib,tarfile,urllib.request
url='https://registry.npmjs.org/typescript/-/typescript-5.8.3.tgz'
expected='p1diW6TqL9L07nNxvRMM7hMMw4c5XOo/1ibL4aAIGmSAt9slTE1Xgw5KWuof2uTOvCg9BY7ZRi+GaF+7sfgPeQ=='
with urllib.request.urlopen(url,timeout=60) as response:raw=response.read(8*1024*1024)
assert base64.b64encode(hashlib.sha512(raw).digest()).decode()==expected,'TypeScript package integrity mismatch'
base=pathlib.Path('android/app/src/main/assets')
with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as archive:
    for member,name in [('package/lib/typescript.js','typescript.js'),('package/LICENSE.txt','typescript-Apache-2.0.txt')]:
        data=archive.extractfile(member).read();(base/name).write_bytes(data)
print('Official TypeScript 5.8.3 compiler prepared; npm integrity verified')
