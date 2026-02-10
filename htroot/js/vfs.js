/**
 * Virtual File System (VFS) using IndexedDB with Bash-like Commands
 * (C) 2026 by Michael Peter Christen
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt
 * If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This script implements a simple key-value store using IndexedDB, which is a low-level API
 * for client-side storage of significant amounts of structured data, including files/blobs.
 * This API uses indexes to enable high-performance searches of this data.
 *
 * The VFS provides a set of methods for storing, retrieving, and deleting key-value pairs in an
 * IndexedDB object store, where keys represent paths in a file system-like structure.
 * Paths are delimited by a slash (/) and must start with a leading slash.
 * Paths ending with a slash are considered directories, while paths not ending with a slash are considered files.
 *
 * The VFS includes Bash-like commands for manipulating the virtual file system.
 *
 * The VFS is exposed as a global object (window.vfs) for easy access from other parts of the application.
 */

// Open a connection to the database
const openRequest = indexedDB.open('vfs', 1);

let vfsReadyResolve;
let vfsReadyReject;
window.vfsReady = new Promise((resolve, reject) => {
  vfsReadyResolve = resolve;
  vfsReadyReject = reject;
});

let db;

const applyUnifiedDiff = (originalText, diffText) => {
  const originalEndsWithNewline = originalText.endsWith('\n');
  const originalLines = originalText.split('\n');
  const diffLines = diffText.split('\n');
  const result = [];
  let origIndex = 0;
  let i = 0;
  const hunkHeader = /^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@/;

  const collectHunkOps = (startIndex) => {
    const ops = [];
    let index = startIndex;
    while (index < diffLines.length && !diffLines[index].startsWith('@@')) {
      const hunkLine = diffLines[index];
      if (hunkLine.startsWith(' ') || hunkLine.startsWith('-') || hunkLine.startsWith('+')) {
        ops.push(hunkLine);
      }
      index += 1;
    }
    return { ops, nextIndex: index };
  };

  const hunkFitsAt = (startAt, ops) => {
    let checkIndex = startAt;
    for (const op of ops) {
      if (op.startsWith(' ') || op.startsWith('-')) {
        const content = op.slice(1);
        if (originalLines[checkIndex] !== content) {
          return false;
        }
        checkIndex += 1;
      }
    }
    return true;
  };

  const findHunkStart = (fromIndex, ops, suggestedStart) => {
    if (Number.isInteger(suggestedStart) && suggestedStart >= fromIndex) {
      if (hunkFitsAt(suggestedStart, ops)) {
        return suggestedStart;
      }
    }
    const firstContext = ops.find((op) => op.startsWith(' '));
    if (!firstContext) {
      return fromIndex;
    }
    const needle = firstContext.slice(1);
    for (let idx = fromIndex; idx < originalLines.length; idx += 1) {
      if (originalLines[idx] === needle && hunkFitsAt(idx, ops)) {
        return idx;
      }
    }
    return -1;
  };

  while (i < diffLines.length) {
    const line = diffLines[i];
    if (
      line.startsWith('diff --git') ||
      line.startsWith('index ') ||
      line.startsWith('--- ') ||
      line.startsWith('+++ ')
    ) {
      i += 1;
      continue;
    }
    const match = line.match(hunkHeader);
    if (!match) {
      i += 1;
      continue;
    }

    const oldStart = parseInt(match[1], 10) - 1;
    const { ops, nextIndex } = collectHunkOps(i + 1);
    const hunkStart = findHunkStart(origIndex, ops, oldStart);
    if (hunkStart < 0) {
      throw new Error('Patch context mismatch.');
    }

    while (origIndex < hunkStart && origIndex < originalLines.length) {
      result.push(originalLines[origIndex]);
      origIndex += 1;
    }

    for (const op of ops) {
      if (op.startsWith(' ')) {
        const content = op.slice(1);
        if (originalLines[origIndex] !== content) {
          throw new Error('Patch context mismatch.');
        }
        result.push(content);
        origIndex += 1;
      } else if (op.startsWith('-')) {
        const content = op.slice(1);
        if (originalLines[origIndex] !== content) {
          throw new Error('Patch removal mismatch.');
        }
        origIndex += 1;
      } else if (op.startsWith('+')) {
        result.push(op.slice(1));
      }
    }

    i = nextIndex;
  }

  while (origIndex < originalLines.length) {
    result.push(originalLines[origIndex]);
    origIndex += 1;
  }

  let patched = result.join('\n');
  if (originalEndsWithNewline && !patched.endsWith('\n')) {
    patched += '\n';
  }
  return patched;
};

// Handle the database upgrade event
openRequest.onupgradeneeded = function (event) {
  const db = event.target.result;
  // Check if the object store exists before creating it
  if (!db.objectStoreNames.contains('keyValueStore')) {
    db.createObjectStore('keyValueStore', { keyPath: 'id' });
  }
};

// Handle the successful opening of the database
openRequest.onsuccess = function (event) {
  db = event.target.result;

  // Define the vfs object with methods to interact with the database
  const vfs = {
    put: function (key, value) {
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(['keyValueStore'], 'readwrite');
        const store = transaction.objectStore('keyValueStore');
        const putRequest = store.put({ id: key, value });

        // Handle the successful storage of a key-value pair
        putRequest.onsuccess = function (event) {
          console.log('Key-value pair stored successfully.');
          resolve();
        };
        // Handle errors
        putRequest.onerror = function (event) {
          console.error('Error storing key-value pair:', event.target.errorCode);
          reject(event.target.errorCode);
        };
      });
    },
    getasync: function (key) {
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(['keyValueStore'], 'readonly');
        const store = transaction.objectStore('keyValueStore');
        const getRequest = store.get(key);
        getRequest.onsuccess = function (event) {
          // Check if the key exists before resolving the promise
          if (event.target.result) {
            resolve(event.target.result.value);
          } else {
            reject('Key not found');
          }
        };
        getRequest.onerror = function (event) {
          reject(event.target.errorCode);
        };
      });
    },
    get: async function (key) {
      return await this.getasync(key);
    },
    rm: function (key) {
      const transaction = db.transaction(['keyValueStore'], 'readwrite');
      const store = transaction.objectStore('keyValueStore');
      const deleteRequest = store.delete(key);
      deleteRequest.onsuccess = function (event) {
        console.log('Entry removed successfully.');
      };
      deleteRequest.onerror = function (event) {
        console.error('Error removing entry:', event.target.errorCode);
      };
    },

    // the following methods all consider that keys are paths and have their proper shape:
    // - a path must have a leading slash
    // - a path ending with a slash is considered a directory
    // - a path not ending with a slash is considered a file
    // - directories cannot be created or removed directly,
    // - creating a file creates also the parent directory, removing all files removes the directory.
    touch: function (path) {
      // Create a file at the specified path.
      if (!path.startsWith('/') || path === '') {
        throw new Error('Invalid path');
      }
      const dirPath = path.substring(0, path.lastIndexOf('/')) + '/';
      const fileName = path.substring(path.lastIndexOf('/') + 1);
      const transaction = db.transaction(['keyValueStore'], 'readwrite');
      const store = transaction.objectStore('keyValueStore');
      const putRequest = store.put({ id: path, value: '' });
      putRequest.onsuccess = function (event) {
        console.log(`File created at ${path}`);
      };
      putRequest.onerror = function (event) {
        console.error(`Error creating file at ${path}: ${event.target.errorCode}`);
      };
    },
    rm: function (path) {
      // Remove a file or directory at the specified path.
      if (!path.startsWith('/') || path === '') {
        throw new Error('Invalid path');
      }
      const transaction = db.transaction(['keyValueStore'], 'readwrite');
      const store = transaction.objectStore('keyValueStore');
      const deleteRequest = store.delete(path);
      deleteRequest.onsuccess = function (event) {
        console.log(`Entry removed at ${path}`);
      };
      deleteRequest.onerror = function (event) {
        console.error(`Error removing entry at ${path}: ${event.target.errorCode}`);
      };
    },
    cp: function (srcPath, destPath) {
      // Copy a file or directory from one path to another.
      if (!srcPath.startsWith('/') || !destPath.startsWith('/') || srcPath === '' || destPath === '') {
        throw new Error('Invalid path');
      }
      const transaction = db.transaction(['keyValueStore'], 'readwrite');
      const store = transaction.objectStore('keyValueStore');
      const getRequest = store.get(srcPath);
      getRequest.onsuccess = function (event) {
        const value = event.target.result ? event.target.result.value : {};
        const putRequest = store.put({ id: destPath, value });
        putRequest.onsuccess = function (event) {
          console.log(`Entry copied from ${srcPath} to ${destPath}`);
        };
        putRequest.onerror = function (event) {
          console.error(`Error copying entry from ${srcPath} to ${destPath}: ${event.target.errorCode}`);
        };
      };
      getRequest.onerror = function (event) {
        console.error(`Error getting entry at ${srcPath}: ${event.target.errorCode}`);
      };
    },
    mv: function (srcPath, destPath) {
      // Move or rename a file or directory from one path to another.
      if (!srcPath.startsWith('/') || !destPath.startsWith('/') || srcPath === '' || destPath === '') {
        throw new Error('Invalid path');
      }
      const transaction = db.transaction(['keyValueStore'], 'readwrite');
      const store = transaction.objectStore('keyValueStore');
      const getRequest = store.get(srcPath);
      getRequest.onsuccess = function (event) {
        const value = event.target.result ? event.target.result.value : {};
        const deleteRequest = store.delete(srcPath);
        deleteRequest.onsuccess = function (event) {
          const putRequest = store.put({ id: destPath, value });
          putRequest.onsuccess = function (event) {
            console.log(`Entry moved from ${srcPath} to ${destPath}`);
          };
          putRequest.onerror = function (event) {
            console.error(`Error moving entry from ${srcPath} to ${destPath}: ${event.target.errorCode}`);
          };
        };
        deleteRequest.onerror = function (event) {
          console.error(`Error deleting entry at ${srcPath}: ${event.target.errorCode}`);
        };
      };
      getRequest.onerror = function (event) {
        console.error(`Error getting entry at ${srcPath}: ${event.target.errorCode}`);
      };
    },
    applyDiff: async function (path, diffText) {
      if (!path.startsWith('/') || path === '' || path.endsWith('/')) {
        throw new Error('Invalid file path');
      }
      if (typeof diffText !== 'string') {
        throw new Error('Invalid diff');
      }
      const current = await this.getasync(path);
      const next = applyUnifiedDiff(String(current || ''), diffText);
      await this.put(path, next);
      return next;
    },
    ls: function (path) {
      // List the contents of a directory at the specified path.
      if (!path.endsWith('/')) {
        throw new Error('Invalid directory path');
      }
      const transaction = db.transaction(['keyValueStore'], 'readonly');
      const store = transaction.objectStore('keyValueStore');
      const cursorRange = path === '/' ? IDBKeyRange.lowerBound(path) : IDBKeyRange.bound(path, path.substring(0, path.length - 1) + '\uffff', false, true);
    
      return new Promise((resolve, reject) => {
        const cursorRequest = store.openCursor(cursorRange);
        const contents = [];
        cursorRequest.onsuccess = function (event) {
          const cursor = event.target.result;
          if (cursor) {
            if (cursor.key.startsWith(path)) {
              contents.push(cursor.key.substring(path.length));
            }
            cursor.continue();
          } else {
            resolve(contents);
          }
        };
        cursorRequest.onerror = function (event) {
          reject(`Error listing directory at ${path}: ${event.target.errorCode}`);
        };
      });
    },
    normalizeDirPath: function (path) {
      if (!path || path === '/') {
        return '/';
      }
      if (!path.startsWith('/')) {
        throw new Error('Invalid directory path');
      }
      return path.endsWith('/') ? path : `${path}/`;
    },
    parentDir: function (path) {
      if (!path || path === '/') {
        return '/';
      }
      if (!path.startsWith('/')) {
        throw new Error('Invalid path');
      }
      if (path.endsWith('/')) {
        return this.normalizeDirPath(path);
      }
      const index = path.lastIndexOf('/');
      return index <= 0 ? '/' : path.substring(0, index + 1);
    },
    baseName: function (path) {
      if (!path || path === '/') {
        return '';
      }
      const trimmed = path.endsWith('/') ? path.slice(0, -1) : path;
      const parts = trimmed.split('/').filter(Boolean);
      return parts[parts.length - 1] || '';
    },
    mkdir: async function (path) {
      const dirPath = this.normalizeDirPath(path);
      await this.put(dirPath, '');
      return dirPath;
    },
    deleteTree: async function (path) {
      if (!path || !path.startsWith('/')) {
        throw new Error('Invalid path');
      }
      if (path.endsWith('/')) {
        const contents = await this.ls(path);
        for (const entry of contents) {
          if (!entry) continue;
          await this.rm(`${path}${entry}`);
        }
        if (path !== '/') {
          await this.rm(path);
        }
        return;
      }
      await this.rm(path);
    },
    moveTree: async function (srcPath, destDir) {
      if (!srcPath || !srcPath.startsWith('/')) {
        throw new Error('Invalid source path');
      }
      const targetDir = this.normalizeDirPath(destDir);
      const isFolder = srcPath.endsWith('/');
      const name = this.baseName(srcPath);
      if (!name) {
        return srcPath;
      }
      const destPath = `${targetDir}${name}${isFolder ? '/' : ''}`;

      if (destPath === srcPath) {
        return destPath;
      }
      if (isFolder && targetDir.startsWith(srcPath)) {
        throw new Error('Cannot move a folder into itself.');
      }

      if (targetDir !== '/') {
        await this.mkdir(targetDir);
      }

      if (isFolder) {
        const contents = await this.ls(srcPath);
        for (const entry of contents) {
          if (!entry) continue;
          await this.mv(`${srcPath}${entry}`, `${destPath}${entry}`);
        }
      }

      await this.mv(srcPath, destPath);
      return destPath;
    },
    cat: function (path) {
      // Display the contents of a file at the specified path.
      if (path.endsWith('/')) {
        throw new Error('Invalid file path');
      }
      const transaction = db.transaction(['keyValueStore'], 'readonly');
      const store = transaction.objectStore('keyValueStore');
      const getRequest = store.get(path);
      getRequest.onsuccess = function (event) {
        console.log(event.target.result ? event.target.result.value : '');
      };
      getRequest.onerror = function (event) {
        console.error(`Error getting file at ${path}: ${event.target.errorCode}`);
      };
    },
    find: function (pattern) {
      // Search for files or directories matching a specified pattern.
      const transaction = db.transaction(['keyValueStore'], 'readonly');
      const store = transaction.objectStore('keyValueStore');
      const cursorRequest = store.openCursor();
      const matches = [];
      cursorRequest.onsuccess = function (event) {
        const cursor = event.target.result;
        if (cursor) {
          if (new RegExp(pattern).test(cursor.key)) {
            matches.push(cursor.key);
          }
          cursor.continue();
        } else {
          console.log(matches.join('\n'));
        }
      };
      cursorRequest.onerror = function (event) {
        console.error(`Error finding pattern ${pattern}: ${event.target.errorCode}`);
      };
    },

    du: function () {
      // Show the disk usage of files and directories.
      const transaction = db.transaction(['keyValueStore'], 'readonly');
      const store = transaction.objectStore('keyValueStore');
      let totalSize = 0;

      return new Promise((resolve, reject) => {
        const cursorRequest = store.openCursor();
        cursorRequest.onsuccess = function (event) {
          const cursor = event.target.result;
          if (cursor) {
            const keyLength = cursor.key ? cursor.key.length : 0;
            const valueLength = cursor.value ? cursor.value.length : 0;
            if (!isNaN(keyLength) && !isNaN(valueLength)) {
              totalSize += keyLength + valueLength;
            }
            cursor.continue();
          } else {
            resolve(totalSize);
          }
        };
        cursorRequest.onerror = function (event) {
          reject(`Error getting disk usage: ${event.target.errorCode}`);
        };
      });
    },
    df: function () {
      // Show the amount of disk space used and available on the file system.
      if (navigator.storage && navigator.storage.estimate) {
        return navigator.storage.estimate().then((estimate) => {
          const quota = Number.isFinite(estimate.quota) ? estimate.quota : 0;
          const usage = Number.isFinite(estimate.usage) ? estimate.usage : 0;
          const available = Math.max(0, quota - usage);
          return { quota, usage, available };
        });
      }
      return Promise.reject('Storage estimate not available');
    },
    grep: function (path, pattern) {
      // Search for a pattern in file content at the specified path.
      if (!path.startsWith('/') || path === '') {
        throw new Error('Invalid path');
      }
      const transaction = db.transaction(['keyValueStore'], 'readonly');
      const store = transaction.objectStore('keyValueStore');
      const getRequest = store.get(path);
      getRequest.onsuccess = function (event) {
        const content = event.target.result ? event.target.result.value : '';
        if (new RegExp(pattern).test(content)) {
          console.log(`${path}: ${content}`);
        }
      };
      getRequest.onerror = function (event) {
        console.error(`Error getting file at ${path}: ${event.target.errorCode}`);
      };
    }
  };

  // Attach the vfs object to the window object
  window.vfs = vfs;
  if (vfsReadyResolve) vfsReadyResolve(vfs);
};

// Handle errors when opening the database
openRequest.onerror = function (event) {
  console.error('Error opening database:', event.target.errorCode);
  if (vfsReadyReject) vfsReadyReject(event.target.errorCode);
};
