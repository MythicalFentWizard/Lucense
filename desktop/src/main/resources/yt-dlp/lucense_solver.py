# Written by Lucense; rewritten whenever it changes.
#
# yt-dlp solves YouTube's challenges by preparing the player script (about
# three megabytes) and running it. In QuickJS the preparing takes about five
# seconds, done again for every video. yt-dlp can keep the prepared player but
# ships with that switched off; this is the same solver with it on, so only
# the first video of each player pays. With the prepared player kept, the
# player's signature timestamp is kept too, as yt-dlp means to once this cache
# is on, so the player isn't downloaded again just to read that number.
#
# Should yt-dlp change underneath this, the import fails or the patch is left
# out, and yt-dlp's own solver carries on as before.
import inspect

from yt_dlp.extractor.youtube.jsc._builtin import vendor
from yt_dlp.extractor.youtube.jsc._builtin.quickjs import QuickJSJCP
from yt_dlp.extractor.youtube.jsc.provider import register_preference, register_provider


@register_provider
class LucenseQuickJSJCP(QuickJSJCP):
    PROVIDER_NAME = 'lucense-quickjs'
    # Kept apart by solver version: a player prepared by another version is never used.
    _CACHE_SECTION = f'lucense-solver-{vendor.VERSION}'
    _ENABLE_PREPROCESSED_PLAYER_CACHE = True


@register_preference(LucenseQuickJSJCP)
def _lucense_first(provider, requests):
    return 900


def _keep_signature_timestamp():
    from yt_dlp.extractor.youtube._video import YoutubeIE

    load = YoutubeIE._load_player_data_from_cache
    store = YoutubeIE._store_player_data_to_cache
    if not all('use_disk_cache' in inspect.signature(f).parameters for f in (load, store)):
        return

    def load_kept(self, name, *args, **kwargs):
        if name == 'sts':
            kwargs['use_disk_cache'] = True
        return load(self, name, *args, **kwargs)

    def store_kept(self, data, name, *args, **kwargs):
        if name == 'sts':
            kwargs['use_disk_cache'] = True
        return store(self, data, name, *args, **kwargs)

    YoutubeIE._load_player_data_from_cache = load_kept
    YoutubeIE._store_player_data_to_cache = store_kept


try:
    _keep_signature_timestamp()
except Exception:
    pass
