import React, { useState } from "react";
import {
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  ActivityIndicator,
} from "react-native";
import { useRouter } from "expo-router";
import { Screen } from "@/components/Screen";
import { colors } from "@/theme/colors";
import { authService } from "@/services/authService";
import { useAuthStore } from "@/store/authStore";

export default function LoginScreen() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const router = useRouter();
  const setAuth = useAuthStore((state) => state.setAuth);

  const handleLogin = async () => {
    if (!email || !password) {
      setError("请填写邮箱和密码");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await authService.login(email, password);
      setAuth(response.user, response.accessToken, response.refreshToken);
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败，请稍后再试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen title="欢迎回来">
      <View style={styles.container}>
        <Text style={styles.subtitle}>登录后继续记录你的生活状态</Text>

        <View style={styles.form}>
          <View style={styles.inputContainer}>
            <Text style={styles.label}>邮箱</Text>
            <TextInput
              style={styles.input}
              placeholder="请输入邮箱"
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
            />
          </View>

          <View style={styles.inputContainer}>
            <Text style={styles.label}>密码</Text>
            <TextInput
              style={styles.input}
              placeholder="请输入密码"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
            />
          </View>

          {error && <Text style={styles.errorText}>{error}</Text>}

          <TouchableOpacity
            style={[styles.button, loading && styles.buttonDisabled]}
            onPress={handleLogin}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.buttonText}>登录</Text>
            )}
          </TouchableOpacity>

          <View style={styles.footer}>
            <Text style={styles.footerText}>还没有账号？</Text>
            <TouchableOpacity onPress={() => router.push("/register")}>
              <Text style={styles.linkText}>去注册</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.demoInfo}>
          <Text style={styles.demoText}>演示账号</Text>
          <Text style={styles.demoText}>邮箱：test@example.com</Text>
          <Text style={styles.demoText}>密码：password</Text>
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  subtitle: {
    fontSize: 16,
    color: colors.muted,
    marginBottom: 32,
  },
  form: {
    gap: 20,
  },
  inputContainer: {
    gap: 8,
  },
  label: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.text,
  },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 14,
    fontSize: 16,
    color: colors.text,
  },
  button: {
    backgroundColor: colors.accent,
    borderRadius: 12,
    padding: 16,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 12,
    shadowColor: colors.accent,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 4,
  },
  buttonDisabled: {
    opacity: 0.7,
  },
  buttonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "700",
  },
  errorText: {
    color: colors.error || "#ff4d4f",
    fontSize: 14,
  },
  footer: {
    flexDirection: "row",
    justifyContent: "center",
    marginTop: 16,
  },
  footerText: {
    color: colors.muted,
    fontSize: 14,
  },
  linkText: {
    color: colors.accent,
    fontSize: 14,
    fontWeight: "600",
  },
  demoInfo: {
    marginTop: 40,
    padding: 16,
    backgroundColor: "#f0f0f0",
    borderRadius: 8,
  },
  demoText: {
    fontSize: 12,
    color: "#666",
    fontFamily: "monospace",
  }
});
