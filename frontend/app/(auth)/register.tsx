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

export default function RegisterScreen() {
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const router = useRouter();
  const setAuth = useAuthStore((state) => state.setAuth);

  const handleRegister = async () => {
    if (!email || !password || !displayName || !confirmPassword) {
      setError("请填写所有信息");
      return;
    }

    if (password !== confirmPassword) {
      setError("两次输入的密码不一致");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await authService.register(email, password, displayName);
      setAuth(response.user, response.accessToken, response.refreshToken);
    } catch (err) {
      setError(err instanceof Error ? err.message : "注册失败，请稍后再试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen title="创建账号">
      <View style={styles.container}>
        <Text style={styles.subtitle}>创建账号后即可开始记录和同步生活数据</Text>

        <View style={styles.form}>
          <View style={styles.inputContainer}>
            <Text style={styles.label}>昵称</Text>
            <TextInput
              style={styles.input}
              placeholder="请输入昵称"
              value={displayName}
              onChangeText={setDisplayName}
            />
          </View>

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
              placeholder="请设置密码"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
            />
          </View>

          <View style={styles.inputContainer}>
            <Text style={styles.label}>确认密码</Text>
            <TextInput
              style={styles.input}
              placeholder="请再次输入密码"
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              secureTextEntry
            />
          </View>

          {error && <Text style={styles.errorText}>{error}</Text>}

          <TouchableOpacity
            style={[styles.button, loading && styles.buttonDisabled]}
            onPress={handleRegister}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.buttonText}>注册</Text>
            )}
          </TouchableOpacity>

          <View style={styles.footer}>
            <Text style={styles.footerText}>已有账号？</Text>
            <TouchableOpacity onPress={() => router.back()}>
              <Text style={styles.linkText}>去登录</Text>
            </TouchableOpacity>
          </View>
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
    gap: 16,
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
});
