package com.example.engine.browser

object ReaderModeExtractor {

    /**
     * JavaScript snippet that extracts reading images and transforms the current page
     * into a high-performance, clutter-free vertical strip reader.
     */
    fun getReaderModeScript(): String {
        return """
            (function() {
                if (window.__mangaflow_reader_active) {
                    location.reload();
                    return;
                }
                window.__mangaflow_reader_active = true;

                // Find comic reader images
                var images = [];
                var selectors = [
                    '#readerarea img',
                    '.reading-content img',
                    '.entry-content img',
                    '.comic-viewer img',
                    '#chapter-video-frame img',
                    'img.wp-manga-chapter-img'
                ];

                for (var s of selectors) {
                    var found = document.querySelectorAll(s);
                    if (found && found.length > 2) {
                        found.forEach(function(img) {
                            var src = img.getAttribute('data-src') || img.getAttribute('src');
                            if (src && !images.includes(src)) images.push(src);
                        });
                        break;
                    }
                }

                // Fallback: look for large vertical images
                if (images.length === 0) {
                    document.querySelectorAll('img').forEach(function(img) {
                        var src = img.getAttribute('data-src') || img.getAttribute('src');
                        if (src && !images.includes(src) && !src.includes('logo') && !src.includes('avatar') && !src.includes('banner')) {
                            images.push(src);
                        }
                    });
                }

                if (images.length === 0) {
                    alert('Reader mode: No chapter images found on this page.');
                    window.__mangaflow_reader_active = false;
                    return;
                }

                // Build clean reader container
                document.body.innerHTML = '';
                document.body.style.backgroundColor = '#0b0c10';
                document.body.style.margin = '0';
                document.body.style.padding = '0';

                var container = document.createElement('div');
                container.style.maxWidth = '800px';
                container.style.margin = '0 auto';
                container.style.display = 'flex';
                container.style.flexDirection = 'column';
                container.style.alignItems = 'center';
                container.style.background = '#0b0c10';

                images.forEach(function(src, index) {
                    var img = document.createElement('img');
                    img.src = src;
                    img.style.width = '100%';
                    img.style.height = 'auto';
                    img.style.display = 'block';
                    img.style.margin = '0';
                    img.style.padding = '0';
                    img.alt = 'Page ' + (index + 1);
                    container.appendChild(img);
                });

                document.body.appendChild(container);
            })();
        """.trimIndent()
    }
}
