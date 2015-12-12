/* 
 * jBrowserDriver (TM)
 * Copyright (C) 2014-2015 Machine Publishers, LLC
 * ops@machinepublishers.com | screenslicer.com | machinepublishers.com
 * Cincinnati, Ohio, USA
 *
 * You can redistribute this program and/or modify it under the terms of the GNU Affero General Public
 * License version 3 as published by the Free Software Foundation.
 *
 * "ScreenSlicer", "jBrowserDriver", "Machine Publishers", and "automatic, zero-config web scraping"
 * are trademarks of Machine Publishers, LLC.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License version 3 for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License version 3 along with this
 * program. If not, see http://www.gnu.org/licenses/
 * 
 * For general details about how to investigate and report license violations, please see
 * https://www.gnu.org/licenses/gpl-violation.html and email the author, ops@machinepublishers.com
 */
package com.machinepublishers.jbrowserdriver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

import com.machinepublishers.browser.Browser;
import com.machinepublishers.jbrowserdriver.Util.Pause;
import com.machinepublishers.jbrowserdriver.Util.Sync;

/**
 * Use this library like any other Selenium WebDriver or RemoteWebDriver
 * (it implements Selenium's JavascriptExecutor, HasInputDevices, TakesScreenshot,
 * Killable, FindsById, FindsByClassName, FindsByLinkText, FindsByName,
 * FindsByCssSelector, FindsByTagName, and FindsByXPath).
 * <p>
 * You can optionally pass a Settings object to the constructor to specify a proxy,
 * request headers, time zone, user agent, or navigator details. By default, the
 * browser mimics the fingerprint of Tor Browser.
 * <p>
 * Also, you can run as many instances of JBrowserDriver as you want (it's thread safe),
 * and the browser sessions will be isolated from each other.
 * <p>
 * Example:
 * 
 * <pre>
 * WebDriver driver = new JBrowserDriver();
 * driver.get("http://example.com"); //This will block for page load and associated AJAX requests.
 * System.out.println(((JBrowserDriver) driver).getStatusCode()); //You can get status code unlike other Selenium drivers! It blocks for AJAX requests and page loads after clicks and keyboard events.
 * System.out.println(driver.getPageSource());
 * </pre>
 */
public class JBrowserDriver implements Browser {
  /**
   * Use this string on sendKeys functions to delete text.
   */
  public static final String KEYBOARD_DELETE;

  static {
    final int CHARS_TO_DELETE = 60;
    StringBuilder builder = new StringBuilder();
    String key = Keys.BACK_SPACE.toString();
    for (int i = 0; i < CHARS_TO_DELETE; i++) {
      builder.append(key);
    }
    key = Keys.DELETE.toString();
    for (int i = 0; i < CHARS_TO_DELETE; i++) {
      builder.append(key);
    }
    KEYBOARD_DELETE = builder.toString();
  }

  final BrowserContext context;

  /**
   * Constructs a browser with default settings, UTC timezone, and no proxy.
   */
  public JBrowserDriver() {
    this(Settings.builder().build());
  }

  /**
   * Use Settings.Builder to create settings to pass to this constructor.
   * 
   * @param settings
   */
  public JBrowserDriver(final Settings settings) {
    context = new BrowserContext(new Settings(settings));
  }

  /**
   * Optionally call this method if you want JavaFX initialized and the browser
   * window opened immediately. Otherwise, initialization will happen lazily.
   */
  public void init() {
    context.init(this);
  }

  /**
   * @return Temporary directory where generated runtime files are saved.
   */
  public File temporaryDir() {
    return JavaFx.tmpDir(context.settingsId.get());
  }

  /**
   * Reset the state of the browser. More efficient than quitting the
   * browser and creating a new instance.
   * 
   * @param settings
   *          New settings to take effect, superseding the original ones
   */
  public void reset(final Settings settings) {
    Util.exec(Pause.SHORT, new AtomicInteger(-1), new Sync<Object>() {
      @Override
      public Object perform() {
        context.item().engine.get().call("getLoadWorker").call("cancel");
        return null;
      }
    }, context.settingsId.get());
    JavaFx.getStatic("com.sun.javafx.webkit.Accessor", context.settings.get().id()).call("getPageFor", context.item().engine.get()).call("stop");
    context.settings.set(new Settings(settings, context.settingsId.get()));
    context.reset(this);
    context.settings.get().cookieStore().clear();
    StatusMonitor.get(context.settings.get().id()).clearStatusMonitor();
    context.logs.get().clear();
  }

  /**
   * Reset the state of the browser. More efficient than quitting the
   * browser and creating a new instance.
   */
  public void reset() {
    reset(context.settings.get());
  }

  @Override
  public String getPageSource() {
    init();
    WebElement element = Element.create(context).findElementByTagName("html");
    return element == null ? null : element.getAttribute("outerHTML");
  }

  @Override
  public String getCurrentUrl() {
    init();
    return Util.exec(Pause.NONE, context.statusCode, new Sync<String>() {
      public String perform() {
        return context.item().view.get().call("getEngine").call("getLocation").toString();
      }
    }, context.settingsId.get());
  }

  @Override
  public int getStatusCode() {
    init();
    try {
      synchronized (context.statusCode) {
        if (context.statusCode.get() == 0) {
          context.statusCode.wait(context.timeouts.get().getPageLoadTimeoutMS());
        }
      }
    } catch (InterruptedException e) {
      context.logs.get().exception(e);
    }
    return context.statusCode.get();
  }

  @Override
  public String getTitle() {
    init();
    return Util.exec(Pause.NONE, context.statusCode, new Sync<String>() {
      public String perform() {
        return context.item().view.get().call("getEngine").call("getTitle").toString();
      }
    }, context.settingsId.get());
  }

  @Override
  public void get(final String url) {
    init();
    Util.exec(Pause.SHORT, context.statusCode, new Sync<Object>() {
      public Object perform() {
        context.item().engine.get().call("load", url);
        return null;
      }
    }, context.settingsId.get());
    try {
      synchronized (context.statusCode) {
        if (context.statusCode.get() == 0) {
          context.statusCode.wait(context.timeouts.get().getPageLoadTimeoutMS());
        }
      }
    } catch (InterruptedException e) {
      context.logs.get().exception(e);
    }
    if (context.statusCode.get() == 0) {
      Util.exec(Pause.SHORT, new AtomicInteger(-1), new Sync<Object>() {
        @Override
        public Object perform() {
          context.item().engine.get().call("getLoadWorker").call("cancel");
          return null;
        }
      }, context.settingsId.get());
    }
  }

  @Override
  public WebElement findElement(By by) {
    init();
    return by.findElement(this);
  }

  @Override
  public List<WebElement> findElements(By by) {
    init();
    return by.findElements(this);
  }

  @Override
  public WebElement findElementById(String id) {
    init();
    return Element.create(context).findElementById(id);
  }

  @Override
  public List<WebElement> findElementsById(String id) {
    init();
    return Element.create(context).findElementsById(id);
  }

  @Override
  public WebElement findElementByXPath(String expr) {
    init();
    return Element.create(context).findElementByXPath(expr);
  }

  @Override
  public List<WebElement> findElementsByXPath(String expr) {
    init();
    return Element.create(context).findElementsByXPath(expr);
  }

  @Override
  public WebElement findElementByLinkText(final String text) {
    init();
    return Element.create(context).findElementByLinkText(text);
  }

  @Override
  public WebElement findElementByPartialLinkText(String text) {
    init();
    return Element.create(context).findElementByPartialLinkText(text);
  }

  @Override
  public List<WebElement> findElementsByLinkText(String text) {
    init();
    return Element.create(context).findElementsByLinkText(text);
  }

  @Override
  public List<WebElement> findElementsByPartialLinkText(String text) {
    init();
    return Element.create(context).findElementsByPartialLinkText(text);
  }

  @Override
  public WebElement findElementByClassName(String cssClass) {
    init();
    return Element.create(context).findElementByClassName(cssClass);
  }

  @Override
  public List<WebElement> findElementsByClassName(String cssClass) {
    init();
    return Element.create(context).findElementsByClassName(cssClass);
  }

  @Override
  public WebElement findElementByName(String name) {
    init();
    return Element.create(context).findElementByName(name);
  }

  @Override
  public List<WebElement> findElementsByName(String name) {
    init();
    return Element.create(context).findElementsByName(name);
  }

  @Override
  public WebElement findElementByCssSelector(String expr) {
    init();
    return Element.create(context).findElementByCssSelector(expr);
  }

  @Override
  public List<WebElement> findElementsByCssSelector(String expr) {
    init();
    return Element.create(context).findElementsByCssSelector(expr);
  }

  @Override
  public WebElement findElementByTagName(String tagName) {
    init();
    return Element.create(context).findElementByTagName(tagName);
  }

  @Override
  public List<WebElement> findElementsByTagName(String tagName) {
    init();
    return Element.create(context).findElementsByTagName(tagName);
  }

  @Override
  public Object executeAsyncScript(String script, Object... args) {
    init();
    return Element.create(context).executeAsyncScript(script, args);
  }

  @Override
  public Object executeScript(String script, Object... args) {
    init();
    return Element.create(context).executeScript(script, args);
  }

  @Override
  public Keyboard getKeyboard() {
    init();
    return context.keyboard.get();
  }

  @Override
  public Mouse getMouse() {
    init();
    return context.mouse.get();
  }

  @Override
  public Capabilities getCapabilities() {
    init();
    return context.capabilities.get();
  }

  @Override
  public void close() {
    init();
    context.removeItem();
  }

  @Override
  public String getWindowHandle() {
    init();
    return context.itemId();
  }

  @Override
  public Set<String> getWindowHandles() {
    init();
    return context.itemIds();
  }

  @Override
  public Options manage() {
    init();
    return context.options.get();
  }

  @Override
  public Navigation navigate() {
    init();
    return context.item().navigation.get();
  }

  @Override
  public void quit() {
    Util.exec(Pause.SHORT, new AtomicInteger(-1), new Sync<Object>() {
      @Override
      public Object perform() {
        context.item().engine.get().call("getLoadWorker").call("cancel");
        return null;
      }
    }, context.settingsId.get());
    JavaFx.getStatic("com.sun.javafx.webkit.Accessor", context.settings.get().id()).call("getPageFor", context.item().engine.get()).call("stop");
    if (Settings.headless()) {
      JavaFx.getStatic("javafx.application.Platform", context.settings.get().id()).call("exit");
    }
    SettingsManager.close(context.settings.get().id());
    context.settings.get().cookieStore().clear();
    StatusMonitor.get(context.settings.get().id()).clearStatusMonitor();
    StatusMonitor.remove(context.settings.get().id());
    JavaFx.close(context.settings.get().id());
    Logs.close(context.settingsId.get());
  }

  @Override
  public TargetLocator switchTo() {
    init();
    return context.targetLocator.get();
  }

  @Override
  public void kill() {
    quit();
  }

  @Override
  public <X> X getScreenshotAs(final OutputType<X> outputType) throws WebDriverException {
    if (Settings.headless()) {
      init();
      final AtomicInteger bytesPerComponent = new AtomicInteger();
      final AtomicInteger width = new AtomicInteger();
      final AtomicInteger height = new AtomicInteger();
      byte[] bytes = Util.exec(Pause.NONE, context.statusCode, new Sync<byte[]>() {
        public byte[] perform() {
          JavaFxObject pixels = context.robot.get().screenshot();
          bytesPerComponent.set((int) pixels.call("getBytesPerComponent").unwrap());
          width.set((int) pixels.call("getWidth").unwrap());
          height.set((int) pixels.call("getHeight").unwrap());
          JavaFxObject pixelBuffer = pixels.call("asByteBuffer");
          byte[] bytes = new byte[(int) (pixelBuffer.call("remaining").unwrap())];
          pixelBuffer.call("get", new Object[] { bytes });
          return bytes;
        }
      }, context.settingsId.get());

      return outputType.convertFromPngBytes((byte[]) JavaFx.getStatic("com.machinepublishers.jbrowserdriver.DynamicScreenshot", context.settingsId.get())
          .call("toPng", bytes, width.get(), height.get(), bytesPerComponent.get()).unwrap());
    } else {
      init();
      JavaFxObject image = Util.exec(Pause.NONE, context.statusCode, new Sync<JavaFxObject>() {
        public JavaFxObject perform() {
          return JavaFx.getStatic(
              "javafx.embed.swing.SwingFXUtils", Long.parseLong(context.item().engine.get().call("getUserAgent").toString()))
              .call("fromFXImage", context.item().view.get().call("snapshot", JavaFx.getNew("javafx.scene.SnapshotParameters", context.settingsId.get()),
                  JavaFx.getNew("javafx.scene.image.WritableImage", context.settingsId.get(),
                      (int) Math.rint((Double) context.item().view.get().call("getWidth").unwrap()),
                      (int) Math.rint((Double) context.item().view.get().call("getHeight").unwrap()))),
                  null);
        }
      }, context.settingsId.get());
      ByteArrayOutputStream out = null;
      try {
        out = new ByteArrayOutputStream();
        JavaFx.getStatic("javax.imageio.ImageIO", context.settingsId.get()).call("write", image, "png", out);
        return outputType.convertFromPngBytes(out.toByteArray());
      } catch (Throwable t) {
        context.logs.get().exception(t);
        return null;
      } finally {
        Util.close(out);
      }
    }
  }
}
