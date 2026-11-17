import re
# ---------------- MainActivity
p='app/src/main/java/com/music/bitchord/MainActivity.kt'; s=open(p).read()
a=s.index('''    // What folds [GlassNavBar] between its expanded and inline shapes.''')
b=s.index('''    // The ported ArchiveTune player: when one of its ten styles is in force,''')
s=s[:a]+'''    // What folds [GlassNavBar] between its expanded and inline shapes, as the
    // reference does on every scroll down (docs/ios-glass-reference.md, clip
    // 03-54-11 at 8.3 s). Held here rather than inside the bar because the
    // page's scroll is what drives it, and the page is a sibling of the bar.
'''+s[b:]
old='''    val navBarScroll = rememberFloatingTabBarScrollConnection(
        inlineBehavior = if (miniPlayerBarBehavior == MiniPlayerBarBehavior.ADAPTIVE) {
            FloatingTabBarInlineBehavior.OnScrollDown
        } else {
            FloatingTabBarInlineBehavior.Never
        },
    )'''
assert old in s
a=s.index(old); b=s.index('''    // The tab the app opens on, ported from ArchiveTune's default open tab.''')
s=s[:a]+'''    val navBarScroll = rememberFloatingTabBarScrollConnection(
        inlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown,
    )
'''+s[b:]
import textwrap
old=textwrap.indent(textwrap.dedent('''\
    // Every page's scroll passes through here, so
    // the glass bar collapses on all of them
    // without each one having to know about it —
    // and while the standard bars are drawing,
    // the same scroll drives their compact fold.
    .nestedScroll(navBarScroll)
'''), ' '*32) + ' '*24 + '} else if (miniPlayerBarBehavior == MiniPlayerBarBehavior.ADAPTIVE) {\n' + ' '*28 + 'Modifier.nestedScroll(compactScrollConnection)\n' + ' '*24 + '} else {\n' + ' '*28 + 'Modifier\n' + ' '*24 + '},\n' + ' '*20 + '),\n'
assert old in s, 'nested'
s=s.replace(old, ' '*24 + '} else {\n' + ' '*28 + 'Modifier\n' + ' '*24 + '},\n' + ' '*20 + ')\n' + ' '*20 + '// Every page\'s scroll passes through here, so the bar\n' + ' '*20 + '// folds on all of them without each one having to know.\n' + ' '*20 + '.nestedScroll(navBarScroll),\n')
a=s.index('                if (glassBarStack) Column(')
b=s.index('        val playerRaised = showNowPlaying && playerSong != null && !archiveTunePlayerActive')
seg=s[a:b]
ia=seg.index('                } else Box(')
end=seg.rindex('\n            }\n')
new=seg[:ia].replace('                if (glassBarStack) Column(','                // The one bar, under every player style: iOS 26 Apple Music\'s glass\n                // tab bar with the mini player docked over it (docs/ios-glass-reference.md).\n                Column(',1)+'                }'+seg[end:]
s=s[:a]+new+s[b:]
open(p,'w').write(s)
print('main ok')
