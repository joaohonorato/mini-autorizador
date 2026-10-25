package br.com.vr.miniautorizador.support;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

/**
 * Dispara tarefas ao mesmo tempo para os testes de concorrência.
 */
public final class ExecucaoParalela {

    private ExecucaoParalela() {
    }

    /**
     * Todas as tarefas esperam a mesma largada antes de começar, para disputarem o recurso de verdade.
     * Uma tarefa que falha faz o teste falhar.
     */
    public static <T> List<T> emParalelo(int quantidade, Callable<T> tarefa) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(quantidade)) {
            List<Future<T>> futuros = IntStream.range(0, quantidade)
                    .mapToObj(i -> executor.submit(() -> {
                        largada.await();
                        return tarefa.call();
                    }))
                    .toList();
            largada.countDown();
            return futuros.stream().map(ExecucaoParalela::aguardar).toList();
        }
    }

    private static <T> T aguardar(Future<T> futuro) {
        try {
            return futuro.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
