package net.simplyrin.gitignore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import net.simplyrin.multiprocess.MultiProcess;
import net.simplyrin.rinstream.RinStream;

/**
 * Created by SimplyRin on 2020/12/31.
 *
 * Copyright (c) 2020 SimplyRin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class Controller {

	@FXML private ChoiceBox<String> gitIgnoreBox;
	@FXML private ChoiceBox<String> licenseBox;
	@FXML private TextField name;
	@FXML private TextField year;

	private Map<String, JsonObject> map = new HashMap<>();
	//.gitignore, LICENCE の作成
	@FXML
	private void initialize()  {
		new RinStream();

		MultiProcess mp = new MultiProcess();

		mp.addProcess(() -> {
			try {
				String result = this.getConnection("https://api.github.com/gitignore/templates")
						.replace("\n", "").replace(" ", "").replace("\"", "").replace("[", "").replace("]", "");

				String[] list = result.split(",");
				Arrays.sort(list);

				Platform.runLater(() -> this.gitIgnoreBox.setValue(list[0].trim()));

				for (int i = 0; i < list.length; i++) {
					final String value = list[i].trim();
					System.out.println("GITIGNORE: list[" + i + "]: " + value);
					Platform.runLater(() -> this.gitIgnoreBox.getItems().add(value));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		mp.addProcess(() -> {
			try {
				String result = this.getConnection("https://api.github.com/licenses");

				JsonArray jsonArray = JsonParser.parseString(result).getAsJsonArray();

				for (int i = 0; i < jsonArray.size(); i++) {
					JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();

					String name = jsonObject.get("name").getAsString();
					System.out.println("LICENSE: " + name);
					this.map.put(name, jsonObject);
					Platform.runLater(() -> this.licenseBox.getItems().add(name));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		this.name.setText(System.getProperty("user.name"));
		this.year.setText(String.valueOf(new Date().getYear() + 1900));

		File file = new File("gitignore-creator.json");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			String value = null;
			try {
				value = Files.lines(Paths.get(file.getPath()), StandardCharsets.UTF_8).collect(Collectors.joining(System.getProperty("line.separator")));
			} catch (IOException e) {
				e.printStackTrace();
			}
			final String v = value;
			mp.setFinishedTask(() -> {
				JsonObject jsonObject = JsonParser.parseString(v).getAsJsonObject();

				if (jsonObject.has("name")) {
					Platform.runLater(() -> this.name.setText(jsonObject.get("name").getAsString()));
				}
				if (jsonObject.has("gitignore")) {
					String gitignore = jsonObject.get("gitignore").getAsString();
					Platform.runLater(() -> this.gitIgnoreBox.setValue(gitignore));
				}
				if (jsonObject.has("license")) {
					String license = jsonObject.get("license").getAsString();
					Platform.runLater(() -> this.licenseBox.setValue(license));
				}
			});
		}
		mp.start();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				save();
			}
		});
	}

	@FXML
	private void onCreate(ActionEvent event) {
		System.out.println("Selected .gitignore: " + this.gitIgnoreBox.getValue());
		System.out.println("Selected LICENSE: " + this.licenseBox.getValue());

		// gitignore のダウンロード
		MultiProcess mp = new MultiProcess();
		mp.addProcess(() -> {
			this.save();
		});
		mp.addProcess(() -> {
			try {
				String result = this.getConnection("https://raw.githubusercontent.com/github/gitignore/master/"
						+ this.gitIgnoreBox.getValue() + ".gitignore");

				File file = new File(".gitignore");
				file.createNewFile();
				FileWriter fileWriter = new FileWriter(file);
				fileWriter.write(result);
				fileWriter.close();
				System.out.println(file.getName() + " created.");
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		if (this.licenseBox.getValue() != null) {
			mp.addProcess(() -> {
				try {
					JsonObject jsonObject = this.map.get(this.licenseBox.getValue());
					String result = this.getConnection(jsonObject.get("url").getAsString());

					String body = JsonParser.parseString(result).getAsJsonObject().get("body").getAsString();

					body = body.replace("[year]", this.year.getText());
					body = body.replace("[fullname]", this.name.getText());

					File file = new File("LICENSE.md");
					file.createNewFile();
					FileWriter fileWriter = new FileWriter(file);
					fileWriter.write(body);
					fileWriter.close();
					System.out.println(file.getName() + " created.");
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		mp.setFinishedTask(() -> {
			Platform.runLater(() -> {
				Alert alert = new Alert(Alert.AlertType.INFORMATION);
				alert.setTitle("");
				alert.setHeaderText("完了");
				alert.setContentText(".gitignore と LICENSE.md を作成しました。");
				alert.show();
			});
		});
		mp.start();
	}

	public String getConnection(String url) throws Exception {
		System.out.println("URL: " + url);

		File folder = new File("GitIgnore-Cache");
		folder.mkdirs();

		String[] sp = url.split(Pattern.quote("/"));
		String name = sp[sp.length - 1];

		File file = new File(folder, name);
		if (file.exists()) {
			return Files.lines(Paths.get(file.getPath()), StandardCharsets.UTF_8).collect(Collectors.joining(System.getProperty("line.separator")));
		}

		System.out.println("Connecting to " + url);

		HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
		connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36");
		String result = IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8);

		try {
			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(result);
			fileWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	public void save() {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("name", this.name.getText());
		jsonObject.addProperty("gitignore", this.gitIgnoreBox.getValue());
		jsonObject.addProperty("license", this.licenseBox.getValue());

		System.out.println(jsonObject.toString());

		File file = new File("gitignore-creator.json");
		try {
			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(jsonObject.toString());
			fileWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
