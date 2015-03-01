# Droid-UIScrollView
**Note:** Still WIP!

An attempt at a ScrollView that scrolls both vertically and horizontally. A UI widget that is readily available for iOS, aptly named [UIScrollView]

##Gradle Dependency

Add this in your project app's ```build.gradle```

```sh
repositories {
    maven {
        url "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    }
}

dependencies{
    compile 'com.greycellofp:DroidUIScrollView:1.0.0'
}
```

### Version
1.0.0

### Development

PRs highly appreciated

### Todo's

 - Write Uinit Tests
 - ~~Publish to a central Maven Repository~~ (awaiting central sync)
 - Mimic iOS's Api for [UIScrollView] (or maybe not)
 - Add/Clean Code Comments
 - Implement Nested Scrolling support for [Lollipop]
 - Flags to switch On/Off Vertical/Horizontal scrolling
 - Cleanup code

License
----

Public License

[UIScrollView]:https://developer.apple.com/library/ios/documentation/UIKit/Reference/UIScrollView_Class/index.html
[Lollipop]:http://www.android.com/versions/lollipop-5-0/
