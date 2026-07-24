import { Link } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '@/src/theme';
export default function NotFoundScreen() { return <View style={styles.page}><Text style={styles.title}>This page moved.</Text><Text style={styles.body}>Your data is safe. Return to LifeOS.</Text><Link href="/" style={styles.link}>Go to Today →</Link></View>; }
const styles = StyleSheet.create({ page: { flex: 1, backgroundColor: colors.canvas, alignItems: 'center', justifyContent: 'center', padding: 24 }, title: { color: colors.ink, fontSize: 28, fontWeight: '800' }, body: { color: colors.muted, marginTop: 8 }, link: { color: colors.moss, fontWeight: '800', marginTop: 20 } });
